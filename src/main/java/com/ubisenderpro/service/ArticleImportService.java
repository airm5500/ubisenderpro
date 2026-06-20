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
        String code = val(ligne, req, "code_article");
        String designation = val(ligne, req, "designation");
        String prix = val(ligne, req, "prix_vente");

        if (code == null || code.isEmpty()) { rejeter(rapport, num, "Code article manquant", ligne, importId); return; }
        if (designation == null || designation.isEmpty()) { rejeter(rapport, num, "Désignation manquante", ligne, importId); return; }

        Optional<Article> existant = articleService.parCode(code);
        if (existant.isPresent() && "IGNORER".equalsIgnoreCase(req.getMode())) {
            rapport.setLignesIgnorees(rapport.getLignesIgnorees() + 1);
            ImportSupport.enregistrerDetail(em, importId, num, "IGNORE", "Doublon ignoré (mode IGNORER)", ligne);
            return;
        }
        Article a = existant.orElseGet(Article::new);
        boolean creation = !existant.isPresent();

        a.setCodeArticle(code);
        a.setDesignation(designation);
        if (prix != null && !prix.isEmpty()) a.setPrixVente(parsePrix(prix));
        setIfPresent(val(ligne, req, "code_barres"), a::setCodeBarres);
        setIfPresent(val(ligne, req, "cip"), a::setCip);
        setIfPresent(val(ligne, req, "unite"), a::setUnite);
        setIfPresent(val(ligne, req, "image_url"), a::setImageUrl);
        setIfPresent(val(ligne, req, "lien_produit"), a::setLienProduit);
        String promo = val(ligne, req, "prix_promotionnel");
        if (promo != null && !promo.isEmpty()) a.setPrixPromotionnel(parsePrix(promo));
        String stock = val(ligne, req, "stock_disponible");
        if (stock != null && !stock.isEmpty()) a.setStockDisponible(parsePrix(stock));

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
