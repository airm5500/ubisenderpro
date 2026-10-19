package ci.ubipharm.crm.rest.service.impl;

import static ci.ubipharm.crm.rest.service.impl.ValeursImport.champ;
import static ci.ubipharm.crm.rest.service.impl.ValeursImport.montantLisible;
import static ci.ubipharm.crm.rest.service.impl.ValeursImport.valeur;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.CorrespondanceImportService;
import ci.ubipharm.crm.rest.service.ImportProduitService;
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
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

/**
 * Import du catalogue des produits du Marketing. Un produit deja connu n'est mis a jour que si l'utilisateur le choisit ;
 * une cellule vide n'efface jamais une valeur ; un produit archive n'est pas reactive par un fichier.
 */
@Service
@RequiredArgsConstructor
public class ImportProduitServiceImpl implements ImportProduitService {

    static final String TYPE = "PRODUITS";

    static final List<String> CODE = List.of("code_produit", "code", "reference", "ref", "code_article", "article", "cip", "code_cip");
    static final List<String> LIBELLE = List.of("libelle", "designation", "nom", "nom_produit", "produit", "libelle_produit");
    static final List<String> CATEGORIE = List.of("categorie", "famille", "gamme", "classe", "classe_therapeutique");
    static final List<String> LABORATOIRE = List.of("laboratoire", "labo", "fabricant", "fournisseur", "marque");
    static final List<String> CONDITIONNEMENT = List.of("conditionnement", "presentation", "forme", "unite");
    static final List<String> PRIX = List.of("prix", "prix_public", "pp", "prix_de_vente", "prix_unitaire", "ppv");
    static final List<String> DESCRIPTION = List.of("description", "commentaire", "observation", "details");

    /** Champs proposes par l'assistant, dans l'ordre de l'exemplaire. */
    static final List<ChampImportDTO> CHAMPS = List.of(
            champ("Code produit", true, "3400930000001", CODE),
            champ("Libellé", true, "Paracétamol 500 mg comprimés", LIBELLE),
            champ("Catégorie", false, "Antalgiques", CATEGORIE),
            champ("Laboratoire", false, "Sanofi", LABORATOIRE),
            champ("Conditionnement", false, "Boîte de 16", CONDITIONNEMENT),
            champ("Prix public", false, "1250", PRIX),
            champ("Description", false, "", DESCRIPTION));

    private static final int TAILLE_LOT = 1000;

    private final EntityManager em;
    private final LecteurTableurService lecteurTableurService;
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
        return "PRODUITS_GERER";
    }

    @Override
    public List<ChampImportDTO> champs() {
        return CHAMPS;
    }

    @Override
    @Transactional
    public ImportDTO simuler(MultipartFile fichier, ParametresImportDTO parametres) {
        TableurDTO tableur = lecteurTableurService.lire(fichier);
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
        // Une correction peut lever un doublon sur une autre ligne : tout le fichier est re-evalue
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
            // L'etat de la base a pu changer depuis la simulation : chaque ligne est re-evaluee
            Evaluation evaluation = evaluer(ligne.getNumeroLigne(), ligne.getDonnees(), contexte);
            ResultatLigneDTO verdict = evaluation.verdict();
            ligne.setMessage(verdict.getMessage());
            switch (verdict.getAction()) {
                case "CREATION" -> {
                    creer(evaluation.lu(), id, utilisateurId);
                    ligne.setResultat("CREE");
                }
                case "MISE_A_JOUR" -> {
                    if (choix.isToutesLesMisesAJour() || clesChoisies.contains(verdict.getCle().toUpperCase(Locale.ROOT))) {
                        mettreAJour(evaluation.existant(), evaluation.lu(), id, utilisateurId);
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
        String code = valeur(valeurs, CODE);
        if (code == null) {
            return rejet(null, "Code produit manquant.");
        }
        String cle = code.toUpperCase(Locale.ROOT);
        Integer premiereLigne = contexte.lignesVues().putIfAbsent(cle, numeroLigne);
        if (premiereLigne != null && premiereLigne != numeroLigne) {
            return rejet(code, "Code déjà présent ligne " + premiereLigne + " du fichier.");
        }

        List<String> motifs = new ArrayList<>();
        longueur(code, 50, "Code produit", motifs);
        String libelle = valeur(valeurs, LIBELLE);
        ProduitExistant existant = contexte.existants().get(cle);
        if (libelle == null && existant == null) {
            motifs.add("Libellé manquant.");
        }
        longueur(libelle, 200, "Libellé", motifs);
        String categorie = valeur(valeurs, CATEGORIE);
        longueur(categorie, 100, "Catégorie", motifs);
        String laboratoire = valeur(valeurs, LABORATOIRE);
        longueur(laboratoire, 150, "Laboratoire", motifs);
        String conditionnement = valeur(valeurs, CONDITIONNEMENT);
        longueur(conditionnement, 100, "Conditionnement", motifs);
        String description = valeur(valeurs, DESCRIPTION);
        longueur(description, 1000, "Description", motifs);
        BigDecimal prix = null;
        String prixSaisi = valeur(valeurs, PRIX);
        if (prixSaisi != null) {
            prix = ValeursImport.montant(prixSaisi).map(m -> m.setScale(2, RoundingMode.HALF_UP)).orElse(null);
            if (prix == null) {
                motifs.add("Prix illisible : " + prixSaisi + ".");
            } else if (prix.signum() < 0) {
                motifs.add("Le prix ne peut pas être négatif (" + montantLisible(prix) + ").");
            }
        }
        if (existant != null && "ARCHIVE".equals(existant.statut())) {
            motifs.add("Ce produit est archivé : réactivez-le dans le catalogue avant de le réimporter.");
        }
        if (!motifs.isEmpty()) {
            return rejet(code, String.join(" ", motifs));
        }

        ProduitLu lu = new ProduitLu(code, libelle, categorie, laboratoire, conditionnement, prix, description);
        if (existant == null) {
            return new Evaluation(verdict("CREATION", code, null), lu, null);
        }
        List<String> ecarts = ecarts(existant, lu);
        return ecarts.isEmpty()
                ? new Evaluation(verdict("IDENTIQUE", code, null), lu, existant)
                : new Evaluation(verdict("MISE_A_JOUR", code, String.join(" ; ", ecarts)), lu, existant);
    }

    private void longueur(String valeur, int max, String libelle, List<String> motifs) {
        if (valeur != null && valeur.length() > max) {
            motifs.add(libelle + " trop long (" + max + " caractères au plus).");
        }
    }

    /** Une cellule vide n'efface jamais une valeur existante. */
    private List<String> ecarts(ProduitExistant existant, ProduitLu lu) {
        List<String> ecarts = new ArrayList<>();
        ecart(ecarts, "Libellé", existant.libelle(), lu.libelle());
        ecart(ecarts, "Catégorie", existant.categorie(), lu.categorie());
        ecart(ecarts, "Laboratoire", existant.laboratoire(), lu.laboratoire());
        ecart(ecarts, "Conditionnement", existant.conditionnement(), lu.conditionnement());
        if (lu.prix() != null && (existant.prix() == null || existant.prix().compareTo(lu.prix()) != 0)) {
            ecarts.add("Prix public : " + (existant.prix() == null ? "vide" : montantLisible(existant.prix())) + " -> " + montantLisible(lu.prix()));
        }
        ecart(ecarts, "Description", existant.description(), lu.description());
        return ecarts;
    }

    private void ecart(List<String> ecarts, String champ, String actuel, String importe) {
        if (importe != null && !importe.equals(actuel)) {
            ecarts.add(champ + " : " + (actuel == null ? "vide" : actuel) + " -> " + importe);
        }
    }

    private void creer(ProduitLu lu, Long importId, Long utilisateurId) {
        em.createNativeQuery("""
                insert into crm_produit (code, libelle, categorie, laboratoire, conditionnement, prix, description, statut,
                                         import_id, cree_par, created_at)
                values (:code, :libelle, :categorie, :laboratoire, :conditionnement, :prix, :description, 'ACTIF',
                        :importId, :par, utc_timestamp())
                """)
                .setParameter("code", lu.code())
                .setParameter("libelle", lu.libelle())
                .setParameter("categorie", lu.categorie())
                .setParameter("laboratoire", lu.laboratoire())
                .setParameter("conditionnement", lu.conditionnement())
                .setParameter("prix", lu.prix())
                .setParameter("description", lu.description())
                .setParameter("importId", importId)
                .setParameter("par", utilisateurId)
                .executeUpdate();
    }

    private void mettreAJour(ProduitExistant existant, ProduitLu lu, Long importId, Long utilisateurId) {
        em.createNativeQuery("""
                update crm_produit set libelle = coalesce(:libelle, libelle), categorie = coalesce(:categorie, categorie),
                       laboratoire = coalesce(:laboratoire, laboratoire), conditionnement = coalesce(:conditionnement, conditionnement),
                       prix = coalesce(:prix, prix), description = coalesce(:description, description),
                       import_id = :importId, modifie_par = :par, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("libelle", lu.libelle())
                .setParameter("categorie", lu.categorie())
                .setParameter("laboratoire", lu.laboratoire())
                .setParameter("conditionnement", lu.conditionnement())
                .setParameter("prix", lu.prix())
                .setParameter("description", lu.description())
                .setParameter("importId", importId)
                .setParameter("par", utilisateurId)
                .setParameter("id", existant.id())
                .executeUpdate();
    }

    private Contexte contexte(List<Map<String, String>> lignes) {
        Set<String> codes = new HashSet<>();
        lignes.forEach(v -> {
            String code = valeur(v, CODE);
            if (code != null) {
                codes.add(code);
            }
        });
        return new Contexte(chargerExistants(codes), new HashMap<>());
    }

    private Map<String, ProduitExistant> chargerExistants(Set<String> codes) {
        Map<String, ProduitExistant> existants = new HashMap<>();
        List<String> liste = new ArrayList<>(codes);
        for (int debut = 0; debut < liste.size(); debut += TAILLE_LOT) {
            @SuppressWarnings("unchecked")
            List<Tuple> lignes = em.createNativeQuery("""
                    select id as id, code as code, libelle as libelle, categorie as categorie, laboratoire as laboratoire,
                           conditionnement as conditionnement, prix as prix, description as description, statut as statut
                    from crm_produit where code in (:codes)
                    """, Tuple.class).setParameter("codes", liste.subList(debut, Math.min(debut + TAILLE_LOT, liste.size()))).getResultList();
            for (Tuple t : lignes) {
                ProduitExistant produit = mapTupleToExistant(t);
                existants.put(produit.code().toUpperCase(Locale.ROOT), produit);
            }
        }
        return existants;
    }

    ProduitExistant mapTupleToExistant(Tuple tuple) {
        return new ProduitExistant(
                tuple.get("id", Long.class),
                tuple.get("code", String.class),
                tuple.get("libelle", String.class),
                tuple.get("categorie", String.class),
                tuple.get("laboratoire", String.class),
                tuple.get("conditionnement", String.class),
                tuple.get("prix", BigDecimal.class),
                tuple.get("description", String.class),
                tuple.get("statut", String.class));
    }

    private Evaluation rejet(String cle, String message) {
        return new Evaluation(verdict("REJET", cle, message), null, null);
    }

    private ResultatLigneDTO verdict(String action, String cle, String message) {
        return ResultatLigneDTO.builder().action(action).cle(cle).message(message).build();
    }

    record ProduitLu(String code, String libelle, String categorie, String laboratoire, String conditionnement, BigDecimal prix,
            String description) {
    }

    record ProduitExistant(Long id, String code, String libelle, String categorie, String laboratoire, String conditionnement,
            BigDecimal prix, String description, String statut) {
    }

    record Evaluation(ResultatLigneDTO verdict, ProduitLu lu, ProduitExistant existant) {
    }

    /** Produits deja connus, charges une fois par import ; lignesVues detecte les doublons dans le fichier. */
    record Contexte(Map<String, ProduitExistant> existants, Map<String, Integer> lignesVues) {
    }
}
