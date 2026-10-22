package ci.ubipharm.crm.rest.service.impl;

import static ci.ubipharm.crm.rest.service.impl.ValeursImport.champ;
import static ci.ubipharm.crm.rest.service.impl.ValeursImport.valeur;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.ArchivageService;
import ci.ubipharm.crm.rest.service.CorrespondanceImportService;
import ci.ubipharm.crm.rest.service.ImportPromotionService;
import ci.ubipharm.crm.rest.service.ImportService;
import ci.ubipharm.crm.rest.service.LecteurTableurService;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.dto.ChampImportDTO;
import ci.ubipharm.crm.rest.service.dto.CorrectionLigneDTO;
import ci.ubipharm.crm.rest.service.dto.ImportDTO;
import ci.ubipharm.crm.rest.service.dto.ImportLigneDTO;
import ci.ubipharm.crm.rest.service.dto.ParametresImportDTO;
import ci.ubipharm.crm.rest.service.dto.ResultatLigneDTO;
import ci.ubipharm.crm.rest.service.dto.TableurDTO;
import ci.ubipharm.crm.rest.service.dto.ValidationImportDTO;
import ci.ubipharm.crm.rest.service.impl.AvantagePromotion.Avantage;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

/**
 * Import des produits des promotions : une ligne = une promotion, un produit du catalogue et son avantage. Un fichier
 * peut remplir plusieurs promotions. Le prix promotionnel l'emporte sur les unites gratuites ; les deux ensemble sont refuses.
 */
@Service
@RequiredArgsConstructor
public class ImportPromotionServiceImpl implements ImportPromotionService {

    static final String TYPE = "PROMOTIONS";

    static final List<String> CODE_PROMOTION = List.of("code_promotion", "promotion", "code_promo", "promo");
    static final List<String> CODE_PRODUIT = List.of("code_produit", "code_article", "article", "produit", "cip", "code_cip", "code");
    static final List<String> QUANTITE_MIN = List.of("quantite_minimale", "quantite_min", "qte_min", "quantite", "qte", "minimum");
    static final List<String> UG_QUANTITE = List.of("unites_offertes", "ug", "unites_gratuites", "gratuits", "offerts", "qte_ug");
    static final List<String> UG_POURCENTAGE = List.of("d_unites_gratuites", "ug_pourcentage", "pourcentage_ug", "ug_pct", "pourcentage",
            "taux_ug", "ug_en_pourcentage");
    static final List<String> UG_PLAFOND = List.of("plafond_d_unites_gratuites", "plafond_ug", "plafond", "ug_max", "maximum_ug");
    static final List<String> PRIX_PROMO = List.of("prix_promotionnel", "prix_promo", "prix_remise", "nouveau_prix", "prix");

    /** Champs proposes par l'assistant, dans l'ordre de l'exemplaire. */
    static final List<ChampImportDTO> CHAMPS = List.of(
            champ("Code promotion", true, "NOV26", CODE_PROMOTION),
            champ("Code produit", true, "DOL500", CODE_PRODUIT),
            champ("Quantité minimale", false, "10", QUANTITE_MIN),
            champ("Unités offertes", false, "2", UG_QUANTITE),
            champ("% d'unités gratuites", false, "", UG_POURCENTAGE),
            champ("Plafond d'unités gratuites", false, "", UG_PLAFOND),
            champ("Prix promotionnel", false, "", PRIX_PROMO));

    private static final int TAILLE_LOT = 1000;

    private final EntityManager em;
    private final LecteurTableurService lecteurTableurService;
    private final ArchivageService archivageService;
    private final ImportService importService;
    private final CorrespondanceImportService correspondanceImportService;
    private final PerimetreService perimetreService;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String moduleRequis() {
        return "MARKETING";
    }

    @Override
    public String droitRequis() {
        return "PROMOTIONS_GERER";
    }

    @Override
    public List<ChampImportDTO> champs() {
        return CHAMPS;
    }

    @Override
    @Transactional
    public ImportDTO simuler(MultipartFile fichier, ParametresImportDTO parametres) {
        TableurDTO tableur = lecteurTableurService.lire(fichier);
        archivageService.archiver("imports-promotions", fichier);
        Map<String, String> correspondance = correspondanceImportService.resoudre(TYPE, CHAMPS, tableur.getEntetes(), parametres);
        List<ImportLigneDTO> lignes = correspondanceImportService.appliquer(CHAMPS, tableur, correspondance).stream()
                .map(l -> ImportLigneDTO.builder().numeroLigne(l.getNumero()).donnees(l.getValeurs()).build())
                .toList();
        evaluerLignes(lignes);
        return importService.simulation(TYPE, fichier.getOriginalFilename(), lignes);
    }

    @Override
    @Transactional
    public ImportDTO corriger(Long id, Long ligneId, CorrectionLigneDTO correction) {
        List<ImportLigneDTO> lignes = importService.lignesAValider(id, TYPE);
        ImportLigneDTO ligne = lignes.stream().filter(l -> l.getId().equals(ligneId)).findFirst()
                .orElseThrow(() -> new RessourceIntrouvableException("Ligne introuvable"));
        ligne.setDonnees(ValeursImport.corriger(CHAMPS, ligne.getDonnees(), correction.getValeurs()));
        evaluerLignes(lignes);
        return importService.majSimulation(id, lignes);
    }

    private void evaluerLignes(List<ImportLigneDTO> lignes) {
        Contexte contexte = contexte(lignes.stream().map(ImportLigneDTO::getDonnees).toList());
        for (ImportLigneDTO ligne : lignes) {
            ResultatLigneDTO verdict = evaluer(ligne.getNumeroLigne(), ligne.getDonnees(), contexte).verdict();
            ligne.setCle(verdict.getCle());
            ligne.setAction(verdict.getAction());
            ligne.setMessage(verdict.getMessage());
        }
    }

    @Override
    @Transactional
    public ImportDTO valider(Long id, ValidationImportDTO choix) {
        List<ImportLigneDTO> lignes = importService.lignesAValider(id, TYPE);
        Contexte contexte = contexte(lignes.stream().map(ImportLigneDTO::getDonnees).toList());
        Set<String> clesChoisies = (choix.getClesAMettreAJour() == null ? List.<String>of() : choix.getClesAMettreAJour()).stream()
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Long utilisateurId = perimetreService.perimetreCourant().getUtilisateurId();

        for (ImportLigneDTO ligne : lignes) {
            Evaluation evaluation = evaluer(ligne.getNumeroLigne(), ligne.getDonnees(), contexte);
            ResultatLigneDTO verdict = evaluation.verdict();
            ligne.setMessage(verdict.getMessage());
            switch (verdict.getAction()) {
                case "CREATION" -> {
                    if (evaluation.existante() == null) {
                        creer(evaluation, id, utilisateurId);
                    } else {
                        // Produit retire puis reimporte : sa ligne reprend
                        AvantagePromotion.ecrire(em, evaluation.existante().id(), evaluation.avantage(), id, utilisateurId);
                    }
                    ligne.setResultat("CREE");
                }
                case "MISE_A_JOUR" -> {
                    if (choix.isToutesLesMisesAJour() || clesChoisies.contains(verdict.getCle().toUpperCase(Locale.ROOT))) {
                        AvantagePromotion.ecrire(em, evaluation.existante().id(), evaluation.avantage(), id, utilisateurId);
                            ligne.setResultat("MIS_A_JOUR");
                    } else {
                        ligne.setResultat("IGNORE");
                    }
                }
                case "IDENTIQUE" -> ligne.setResultat("IGNORE");
                default -> ligne.setResultat("REJETE");
            }
        }
        return importService.terminerValidation(id, lignes);
    }

    Evaluation evaluer(int numeroLigne, Map<String, String> valeurs, Contexte contexte) {
        String codePromotion = valeur(valeurs, CODE_PROMOTION);
        String codeProduit = valeur(valeurs, CODE_PRODUIT);
        if (codePromotion == null || codeProduit == null) {
            return rejet(codePromotion == null ? codeProduit : codePromotion,
                    codePromotion == null ? "Code promotion manquant." : "Code produit manquant.");
        }
        String cle = codePromotion + " / " + codeProduit;
        Integer premiereLigne = contexte.lignesVues().putIfAbsent(cle.toUpperCase(Locale.ROOT), numeroLigne);
        if (premiereLigne != null && premiereLigne != numeroLigne) {
            return rejet(cle, "Ce produit est déjà cité pour cette promotion ligne " + premiereLigne + " du fichier.");
        }

        List<String> motifs = new ArrayList<>();
        Promotion promotion = contexte.promotions().get(codePromotion.toUpperCase(Locale.ROOT));
        if (promotion == null) {
            motifs.add("Promotion inconnue : " + codePromotion + ". Créez-la d'abord dans Promotions.");
        } else if ("ARCHIVEE".equals(promotion.statut())) {
            motifs.add("La promotion " + codePromotion + " est archivée.");
        }
        Produit produit = contexte.produits().get(codeProduit.toUpperCase(Locale.ROOT));
        if (produit == null) {
            motifs.add("Produit inconnu : " + codeProduit + ". Ajoutez-le d'abord au catalogue.");
        } else if (!"ACTIF".equals(produit.statut())) {
            motifs.add("Le produit " + codeProduit + " est archivé dans le catalogue.");
        }

        Integer quantiteMin = entier(valeurs, QUANTITE_MIN, "Quantité minimale", motifs);
        Integer ugQuantite = entier(valeurs, UG_QUANTITE, "Unités offertes", motifs);
        BigDecimal ugPourcentage = decimal(valeurs, UG_POURCENTAGE, "% d'unités gratuites", motifs);
        Integer ugPlafond = entier(valeurs, UG_PLAFOND, "Plafond d'unités gratuites", motifs);
        BigDecimal prixPromo = decimal(valeurs, PRIX_PROMO, "Prix promotionnel", motifs);
        String type = null;
        if (prixPromo != null && (ugQuantite != null || ugPourcentage != null)) {
            motifs.add("Choisissez entre unités gratuites et prix promotionnel, pas les deux.");
        } else if (ugQuantite != null && ugPourcentage != null) {
            motifs.add("Choisissez entre unités offertes et pourcentage d'unités gratuites, pas les deux.");
        } else {
            type = prixPromo != null ? AvantagePromotion.PRIX : ugPourcentage != null ? AvantagePromotion.UG_POURCENTAGE
                    : ugQuantite != null ? AvantagePromotion.UG_QUANTITE : null;
        }
        Avantage avantage = Avantage.de(type, quantiteMin == null ? 1 : quantiteMin, ugQuantite, ugPourcentage, ugPlafond, prixPromo);
        if (motifs.isEmpty()) {
            motifs.addAll(AvantagePromotion.controler(avantage, produit == null ? null : produit.prix()));
        }
        if (!motifs.isEmpty()) {
            return rejet(cle, String.join(" ", motifs));
        }

        LigneExistante existante = contexte.existantes().get(promotion.id() + "/" + produit.id());
        Evaluation evaluation = new Evaluation(null, promotion, produit, avantage, existante);
        if (existante == null || !existante.actif()) {
            return evaluation.avec(verdict("CREATION", cle, AvantagePromotion.resume(avantage, produit.prix())));
        }
        String actuel = AvantagePromotion.resume(existante.avantage(), produit.prix());
        String nouveau = AvantagePromotion.resume(avantage, produit.prix());
        return memeAvantage(existante.avantage(), avantage)
                ? evaluation.avec(verdict("IDENTIQUE", cle, null))
                : evaluation.avec(verdict("MISE_A_JOUR", cle, "Avantage : " + actuel + " -> " + nouveau));
    }

    private static boolean memeAvantage(Avantage a, Avantage b) {
        return Objects.equals(a.type(), b.type()) && a.quantiteMin() == b.quantiteMin() && Objects.equals(a.ugQuantite(), b.ugQuantite())
                && egal(a.ugPourcentage(), b.ugPourcentage()) && Objects.equals(a.ugPlafond(), b.ugPlafond()) && egal(a.prixPromo(), b.prixPromo());
    }

    private static boolean egal(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private Integer entier(Map<String, String> valeurs, List<String> alias, String libelle, List<String> motifs) {
        BigDecimal nombre = decimal(valeurs, alias, libelle, motifs);
        if (nombre == null) {
            return null;
        }
        if (nombre.stripTrailingZeros().scale() > 0) {
            motifs.add(libelle + " : nombre entier attendu (" + valeur(valeurs, alias) + ").");
            return null;
        }
        return nombre.intValueExact();
    }

    private BigDecimal decimal(Map<String, String> valeurs, List<String> alias, String libelle, List<String> motifs) {
        String saisi = valeur(valeurs, alias);
        // Dans les fichiers UBIPHARM, une cellule a 0 signifie « non renseigne »
        if (saisi == null || saisi.strip().equals("0")) {
            return null;
        }
        return ValeursImport.montant(saisi.replace("%", "")).map(m -> m.setScale(2, RoundingMode.HALF_UP)).orElseGet(() -> {
            motifs.add(libelle + " illisible : " + saisi + ".");
            return null;
        });
    }

    private void creer(Evaluation e, Long importId, Long utilisateurId) {
        Avantage a = e.avantage();
        em.createNativeQuery("""
                insert into crm_promotion_produit (promotion_id, produit_id, quantite_min, avantage, ug_quantite, ug_pourcentage, ug_plafond,
                                                   prix_promo, actif, import_id, cree_par, created_at)
                values (:p, :pr, :qte, :avantage, :ugq, :ugp, :plafond, :prix, true, :importId, :par, utc_timestamp())
                """)
                .setParameter("p", e.promotion().id()).setParameter("pr", e.produit().id())
                .setParameter("qte", a.quantiteMin()).setParameter("avantage", a.type())
                .setParameter("ugq", a.ugQuantite()).setParameter("ugp", a.ugPourcentage())
                .setParameter("plafond", a.ugPlafond()).setParameter("prix", a.prixPromo())
                .setParameter("importId", importId).setParameter("par", utilisateurId)
                .executeUpdate();
    }

    private Contexte contexte(List<Map<String, String>> lignes) {
        Set<String> codesPromotions = new HashSet<>();
        Set<String> codesProduits = new HashSet<>();
        lignes.forEach(v -> {
            String promotion = valeur(v, CODE_PROMOTION);
            String produit = valeur(v, CODE_PRODUIT);
            if (promotion != null) {
                codesPromotions.add(promotion);
            }
            if (produit != null) {
                codesProduits.add(produit);
            }
        });
        Map<String, Promotion> promotions = new HashMap<>();
        for (List<String> lot : lots(codesPromotions)) {
            @SuppressWarnings("unchecked")
            List<Tuple> t = em.createNativeQuery("select id as id, code as code, statut as statut from crm_promotion where code in (:codes)", Tuple.class)
                    .setParameter("codes", lot).getResultList();
            t.forEach(x -> promotions.put(x.get("code", String.class).toUpperCase(Locale.ROOT),
                    new Promotion(x.get("id", Long.class), x.get("code", String.class), x.get("statut", String.class))));
        }
        Map<String, Produit> produits = new HashMap<>();
        for (List<String> lot : lots(codesProduits)) {
            @SuppressWarnings("unchecked")
            List<Tuple> t = em.createNativeQuery("select id as id, code as code, prix as prix, statut as statut from crm_produit where code in (:codes)",
                    Tuple.class).setParameter("codes", lot).getResultList();
            t.forEach(x -> produits.put(x.get("code", String.class).toUpperCase(Locale.ROOT),
                    new Produit(x.get("id", Long.class), x.get("prix", BigDecimal.class), x.get("statut", String.class))));
        }
        Map<String, LigneExistante> existantes = new HashMap<>();
        if (!promotions.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Tuple> t = em.createNativeQuery("""
                    select id as id, promotion_id as promotionId, produit_id as produitId, quantite_min as quantiteMin, avantage as avantage,
                           ug_quantite as ugQuantite, ug_pourcentage as ugPourcentage, ug_plafond as ugPlafond, prix_promo as prixPromo, actif as actif
                    from crm_promotion_produit where promotion_id in (:ids)
                    """, Tuple.class).setParameter("ids", promotions.values().stream().map(Promotion::id).toList()).getResultList();
            t.forEach(x -> existantes.put(x.get("promotionId", Long.class) + "/" + x.get("produitId", Long.class), mapTupleToExistante(x)));
        }
        return new Contexte(promotions, produits, existantes, new HashMap<>());
    }

    LigneExistante mapTupleToExistante(Tuple tuple) {
        Object actif = tuple.get("actif");
        return new LigneExistante(tuple.get("id", Long.class),
                Avantage.de(tuple.get("avantage", String.class), ((Number) tuple.get("quantiteMin")).intValue(), tuple.get("ugQuantite", Integer.class),
                        tuple.get("ugPourcentage", BigDecimal.class), tuple.get("ugPlafond", Integer.class), tuple.get("prixPromo", BigDecimal.class)),
                actif instanceof Boolean b ? b : actif instanceof Number n && n.intValue() != 0);
    }

    private List<List<String>> lots(Set<String> valeurs) {
        List<String> liste = new ArrayList<>(valeurs);
        List<List<String>> lots = new ArrayList<>();
        for (int debut = 0; debut < liste.size(); debut += TAILLE_LOT) {
            lots.add(liste.subList(debut, Math.min(debut + TAILLE_LOT, liste.size())));
        }
        return lots;
    }

    private Evaluation rejet(String cle, String message) {
        return new Evaluation(verdict("REJET", cle, message), null, null, null, null);
    }

    private ResultatLigneDTO verdict(String action, String cle, String message) {
        return ResultatLigneDTO.builder().action(action).cle(cle).message(message).build();
    }

    record Promotion(Long id, String code, String statut) {
    }

    record Produit(Long id, BigDecimal prix, String statut) {
    }

    record LigneExistante(Long id, Avantage avantage, boolean actif) {
    }

    record Evaluation(ResultatLigneDTO verdict, Promotion promotion, Produit produit, Avantage avantage, LigneExistante existante) {

        Evaluation avec(ResultatLigneDTO verdict) {
            return new Evaluation(verdict, promotion, produit, avantage, existante);
        }
    }

    /** Referentiels charges une fois par import ; lignesVues detecte les doublons dans le fichier. */
    record Contexte(Map<String, Promotion> promotions, Map<String, Produit> produits, Map<String, LigneExistante> existantes,
            Map<String, Integer> lignesVues) {
    }
}
