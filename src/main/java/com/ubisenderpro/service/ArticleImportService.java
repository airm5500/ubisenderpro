package com.ubisenderpro.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubisenderpro.dto.ImportClientRequest;
import com.ubisenderpro.dto.ImportReport;
import com.ubisenderpro.entity.Article;
import com.ubisenderpro.entity.CategorieArticle;
import com.ubisenderpro.entity.ImportLog;
import com.ubisenderpro.entity.ImportMapping;
import com.ubisenderpro.entity.Marque;
import com.ubisenderpro.importer.FileParser;
import com.ubisenderpro.importer.ImportSupport;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Import des articles depuis Excel/CSV (section 14 de la spec).
 * Réutilise le même assistant générique (mapping, doublons, rapport).
 */
@Stateless
public class ArticleImportService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private ArticleService articleService;
    @EJB
    private CatalogueService catalogueService;
    @EJB
    private ImportMappingService importMappingService;
    @EJB
    private PromotionService promotionService;

    public ImportReport importer(ImportClientRequest req, Long utilisateurId) {
        long debut = System.currentTimeMillis();
        ImportReport rapport = new ImportReport();
        rapport.setTypeImport("ARTICLES");

        if (req.getMappingId() != null) {
            Optional<ImportMapping> saved = importMappingService.parId(req.getMappingId());
            saved.ifPresent(m -> {
                try {
                    req.setMapping(MAPPER.readValue(m.getMappingJson(), new TypeReference<Map<String, String>>() {}));
                    if (m.getSeparateur() != null) req.setSeparateur(m.getSeparateur());
                } catch (Exception ignored) { }
            });
        }

        ImportLog log = new ImportLog();
        log.setNomFichier(req.getNomFichier());
        log.setTypeImport("ARTICLES");
        log.setUtilisateurId(utilisateurId);
        log.setModeImport(req.isSimulation() ? "SIMULATION" : req.getMode());
        em.persist(log);

        List<Map<String, String>> lignes;
        try {
            byte[] contenu = Base64.getDecoder().decode(req.getFichierBase64());
            char sep = req.getSeparateur() != null && !req.getSeparateur().isEmpty() ? req.getSeparateur().charAt(0) : ';';
            lignes = FileParser.parse(contenu, req.getNomFichier(), sep);
        } catch (Exception e) {
            log.setStatut("ECHEC");
            log.setFichierErreurs("Lecture du fichier impossible : " + e.getMessage());
            em.merge(log);
            rapport.ajouterErreur(0, "Lecture du fichier impossible : " + e.getMessage());
            return rapport;
        }

        rapport.setLignesLues(lignes.size());
        int num = 1;
        for (Map<String, String> ligne : lignes) {
            num++;
            try {
                traiter(ligne, req, rapport, num, log.getId());
            } catch (Exception e) {
                rapport.setLignesRejetees(rapport.getLignesRejetees() + 1);
                rapport.ajouterErreur(num, e.getMessage());
                ImportSupport.enregistrerDetail(em, log.getId(), num, "REJETE", e.getMessage(), ligne);
            }
        }

        log.setNbLignes(rapport.getLignesLues());
        log.setNbCrees(rapport.getComptesCrees());
        log.setNbMisAJour(rapport.getComptesMisAJour());
        log.setNbRejetes(rapport.getLignesRejetees());
        log.setDureeMs(System.currentTimeMillis() - debut);
        log.setStatut(req.isSimulation() ? "SIMULATION" : "TERMINE");
        if (!rapport.getErreurs().isEmpty()) log.setFichierErreurs(String.join("\n", rapport.getErreurs()));
        em.merge(log);
        rapport.setImportId(log.getId());
        return rapport;
    }

    private void traiter(Map<String, String> ligne, ImportClientRequest req, ImportReport rapport, int num, Long importId) {
        String code = val(ligne, req, "pscode");
        String designation = val(ligne, req, "designation");
        String prix = val(ligne, req, "prix_vente");

        if (code == null || code.isEmpty()) { rejeter(rapport, num, "PS Code manquant", ligne, importId); return; }

        Optional<Article> existant = articleService.parCode(code);
        if (existant.isPresent() && "IGNORER".equalsIgnoreCase(req.getMode())) {
            rapport.setLignesIgnorees(rapport.getLignesIgnorees() + 1);
            ImportSupport.enregistrerDetail(em, importId, num, "IGNORE", "Doublon ignoré (mode IGNORER)", ligne);
            return;
        }
        // En création la désignation est requise ; en mise à jour on accepte une partie
        // des colonnes seulement (mise à jour sélective, ex. dates de promo).
        if (!existant.isPresent() && (designation == null || designation.isEmpty())) {
            rejeter(rapport, num, "Désignation manquante", ligne, importId); return;
        }
        Article a = existant.orElseGet(Article::new);
        boolean creation = !existant.isPresent();

        a.setPscode(code);
        setIfPresent(designation, a::setDesignation);
        if (prix != null && !prix.isEmpty()) a.setPrixVente(parsePrix(prix));
        String prixPublic = val(ligne, req, "prix_vente_public");
        if (prixPublic != null && !prixPublic.isEmpty()) a.setPrixVentePublic(parsePrix(prixPublic));
        setIfPresent(val(ligne, req, "code_barres"), a::setCodeBarres);
        setIfPresent(val(ligne, req, "cip"), a::setCip);
        setIfPresent(val(ligne, req, "unite"), a::setUnite);
        setIfPresent(val(ligne, req, "image_url"), a::setImageUrl);
        setIfPresent(val(ligne, req, "lien_produit"), a::setLienProduit);
        setIfPresent(val(ligne, req, "nom_promo"), a::setNomPromo);
        setIfPresent(val(ligne, req, "code_promo"), a::setCodePromo);
        Integer qteCmd = parseEntier(val(ligne, req, "quantite_commandee"));
        if (qteCmd != null) a.setQuantiteCommandee(qteCmd);
        Integer qteUg = parseEntier(val(ligne, req, "quantite_ug"));
        if (qteUg != null) a.setQuantiteUg(qteUg);
        String promo = val(ligne, req, "prix_promotionnel");
        if (promo != null && !promo.isEmpty()) a.setPrixPromotionnel(parsePrix(promo));
        String stock = val(ligne, req, "stock_disponible");
        if (stock != null && !stock.isEmpty()) a.setStockDisponible(parsePrix(stock));

        // Dates de promo composées : Année promo + mois/jour début (et mois/jour fin).
        java.time.LocalDateTime dDebut = composerDate(val(ligne, req, "promo_annee"),
                val(ligne, req, "promo_mois_debut"), val(ligne, req, "promo_jour_debut"), false);
        if (dDebut != null) a.setDateDebutPromotion(dDebut);
        java.time.LocalDateTime dFin = composerDate(val(ligne, req, "promo_annee"),
                val(ligne, req, "promo_mois_fin"), val(ligne, req, "promo_jour_fin"), true);
        if (dFin != null) a.setDateFinPromotion(dFin);

        // Promotion réutilisable : résolue/créée par code, associée au produit.
        String codePromo = val(ligne, req, "code_promo");
        if (!req.isSimulation() && codePromo != null && !codePromo.isEmpty()) {
            com.ubisenderpro.entity.Promotion p = promotionService.resoudre(
                    codePromo, val(ligne, req, "nom_promo"), dDebut, dFin);
            if (p != null) { a.getPromotions().add(p); }
        }

        String cat = val(ligne, req, "categorie");
        if (cat != null && !cat.isEmpty()) {
            Optional<CategorieArticle> c = catalogueService.resoudreCategorie(cat, req.isCreerSegmentation());
            c.ifPresent(x -> a.setCategorieId(x.getId()));
        }
        String marque = val(ligne, req, "marque");
        if (marque != null && !marque.isEmpty()) {
            Optional<Marque> m = catalogueService.resoudreMarque(marque, req.isCreerSegmentation());
            m.ifPresent(x -> a.setMarqueId(x.getId()));
        }

        if (!req.isSimulation()) {
            if (creation) articleService.creer(a); else articleService.modifier(a);
        }
        if (creation) rapport.setComptesCrees(rapport.getComptesCrees() + 1);
        else rapport.setComptesMisAJour(rapport.getComptesMisAJour() + 1);
    }

    private BigDecimal parsePrix(String v) {
        try {
            return new BigDecimal(v.replaceAll("[^0-9.,-]", "").replace(",", "."));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Integer parseEntier(String v) {
        if (v == null || v.trim().isEmpty()) { return null; }
        try { return (int) Math.round(Double.parseDouble(v.replaceAll("[^0-9.,-]", "").replace(",", "."))); }
        catch (Exception e) { return null; }
    }

    /** Compose une date à partir d'année / mois / jour ; null si l'un manque ou est invalide. */
    private java.time.LocalDateTime composerDate(String annee, String mois, String jour, boolean finJournee) {
        Integer a = parseEntier(annee), m = parseEntier(mois), j = parseEntier(jour);
        if (a == null || m == null || j == null) { return null; }
        try {
            java.time.LocalDate d = java.time.LocalDate.of(a, m, j);
            return finJournee ? d.atTime(23, 59, 59) : d.atStartOfDay();
        } catch (Exception e) { return null; }
    }

    private void rejeter(ImportReport rapport, int num, String msg, Map<String, String> ligne, Long importId) {
        rapport.setLignesRejetees(rapport.getLignesRejetees() + 1);
        rapport.ajouterErreur(num, msg);
        ImportSupport.enregistrerDetail(em, importId, num, "REJETE", msg, ligne);
    }

    private void setIfPresent(String v, java.util.function.Consumer<String> setter) {
        if (v != null && !v.isEmpty()) setter.accept(v);
    }

    private String val(Map<String, String> ligne, ImportClientRequest req, String champ) {
        String col = req.getMapping().getOrDefault(champ, champ);
        String v = ligne.get(col);
        return v == null ? null : v.trim();
    }
}
