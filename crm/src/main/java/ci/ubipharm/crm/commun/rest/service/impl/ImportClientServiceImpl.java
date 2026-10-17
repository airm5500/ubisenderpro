package ci.ubipharm.crm.commun.rest.service.impl;

import static ci.ubipharm.crm.commun.rest.service.impl.ValeursImport.champ;
import static ci.ubipharm.crm.commun.rest.service.impl.ValeursImport.normaliserLibelle;
import static ci.ubipharm.crm.commun.rest.service.impl.ValeursImport.valeur;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.commun.rest.service.CorrespondanceImportService;
import ci.ubipharm.crm.commun.rest.service.ImportClientService;
import ci.ubipharm.crm.commun.rest.service.ImportService;
import ci.ubipharm.crm.commun.rest.service.LecteurTableurService;
import ci.ubipharm.crm.commun.rest.service.ParametreService;
import ci.ubipharm.crm.commun.rest.service.dto.ChampImportDTO;
import ci.ubipharm.crm.commun.rest.service.dto.CorrectionLigneDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ImportDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ImportLigneDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ParametresImportDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ResultatLigneDTO;
import ci.ubipharm.crm.commun.rest.service.dto.TableurDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ValidationImportDTO;
import ci.ubipharm.crm.securite.rest.service.PerimetreService;
import ci.ubipharm.crm.securite.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

/**
 * Import de la base clients commune. Une ligne cree un client inconnu, signale les ecarts d'un client
 * existant (mis a jour seulement si l'utilisateur le choisit) et n'efface jamais une valeur existante
 * avec une cellule vide.
 */
@Service
@RequiredArgsConstructor
public class ImportClientServiceImpl implements ImportClientService {

    static final String TYPE = "CLIENTS";

    static final List<String> CODE = List.of("code_client", "code", "client_code", "numero_client", "n_client",
            "numero_de_compte", "numerodecompte", "numerodeompte", "compte", "n_compte");
    static final List<String> RAISON_SOCIALE = List.of("raison_sociale", "nom_client", "nom", "pharmacie", "libelle");
    static final List<String> AGENCE = List.of("agence", "agce", "code_agence", "agence_code", "libelle_agence");
    static final List<String> SOUS_AGENCE = List.of("sous_agence", "sgce", "secteur");
    static final List<String> SEGMENTATION = List.of("segmentation", "seg", "segment", "categorie");
    static final List<String> VILLE = List.of("ville", "localite", "commune");
    static final List<String> ADRESSE = List.of("adresse", "adresse_postale", "localisation");
    /** Dans le fichier UBIPHARM, la colonne Adresse1 contient le nom du pharmacien. */
    static final List<String> NOM_PHARMACIEN = List.of("nom_pharmacien", "adresse1", "adresse_1", "pharmacien", "nom_du_pharmacien");
    static final List<String> EMAIL = List.of("email", "e_mail", "e_mailmaj", "emailmaj", "mail", "courriel");
    static final List<String> COMMENTAIRE = List.of("commentaire", "commentair", "commentaires", "observation");

    /** Numeros par role : le numero PRINCIPAL est celui des relances WhatsApp. */
    static final Map<String, List<String>> NUMEROS = numeros();

    private static Map<String, List<String>> numeros() {
        Map<String, List<String>> numeros = new LinkedHashMap<>();
        numeros.put("PRINCIPAL", List.of("numero_whatsapp", "whatsappmaj", "whatsapp", "numero_principal", "numero_1", "contact_1",
                "telephone", "telephone_1", "tel", "tel_1"));
        numeros.put("TITULAIRE", List.of("numero_titulaire", "pharmacientitulaire", "pharmacien_titulaire", "titulaire"));
        numeros.put("ASSISTANT", List.of("numero_assistant", "pharmacienass", "pharmacien_ass", "pharmacien_assistant", "assistant"));
        numeros.put("CHARGE_COMMANDE", List.of("numero_charge_commande", "chargedecde", "charge_de_cde", "charge_de_commande", "charge_commande"));
        numeros.put("COMPTABLE", List.of("numero_comptable", "comptable"));
        numeros.put("SECONDAIRE", List.of("numero_secondaire", "numero_2", "contact_2", "telephone_2", "tel_2"));
        return numeros;
    }

    private static final Map<String, String> LIBELLES_NUMEROS = Map.of(
            "PRINCIPAL", "Numero WhatsApp", "TITULAIRE", "Numero du titulaire", "ASSISTANT", "Numero du pharmacien assistant",
            "CHARGE_COMMANDE", "Numero du charge de commande", "COMPTABLE", "Numero du comptable", "SECONDAIRE", "Numero secondaire");

    /** Champs proposes par l'assistant, dans l'ordre de l'exemplaire. */
    static final List<ChampImportDTO> CHAMPS = champsImport();

    private static List<ChampImportDTO> champsImport() {
        List<ChampImportDTO> champs = new ArrayList<>(List.of(
                champ("Numero de compte", true, "5125", CODE),
                champ("Nom de la pharmacie", true, "PHARMACIE DU PLATEAU", RAISON_SOCIALE),
                champ("Agence", true, "Y", AGENCE),
                champ("Sous-agence", false, "Y2", SOUS_AGENCE),
                champ("Segment", false, "P", SEGMENTATION),
                champ("Nom du pharmacien", false, "KOFFI AYA", NOM_PHARMACIEN),
                champ("Ville", false, "Abidjan", VILLE),
                champ("Adresse", false, "Boulevard Latrille", ADRESSE),
                champ("E-mail", false, "contact@pharmacie.ci", EMAIL),
                champ("Commentaire", false, "", COMMENTAIRE)));
        NUMEROS.forEach((fonction, alias) -> champs.add(champ(LIBELLES_NUMEROS.get(fonction), false,
                "PRINCIPAL".equals(fonction) ? "2250700000001" : "", alias)));
        return List.copyOf(champs);
    }

    private static final int TAILLE_LOT = 1000;

    private final EntityManager em;
    private final LecteurTableurService lecteurTableurService;
    private final ImportService importService;
    private final CorrespondanceImportService correspondanceImportService;
    private final PerimetreService perimetreService;
    private final ParametreService parametreService;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String moduleRequis() {
        return null;
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

        for (ImportLigneDTO ligne : lignes) {
            // L'etat de la base a pu changer depuis la simulation : chaque ligne est re-evaluee
            Evaluation evaluation = evaluer(ligne.getNumeroLigne(), ligne.getDonnees(), contexte);
            ResultatLigneDTO verdict = evaluation.verdict();
            ligne.setMessage(verdict.getMessage());
            switch (verdict.getAction()) {
                case "CREATION" -> {
                    creer(evaluation.lu());
                    ligne.setResultat("CREE");
                }
                case "MISE_A_JOUR" -> {
                    if (choix.isToutesLesMisesAJour() || clesChoisies.contains(verdict.getCle().toUpperCase(Locale.ROOT))) {
                        mettreAJour(evaluation.existant(), evaluation.lu());
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
        List<String> motifs = new ArrayList<>();
        if (code == null) {
            return rejet(null, "Code client manquant.");
        }
        String cle = code.toUpperCase(Locale.ROOT);
        Integer premiereLigne = contexte.lignesVues().putIfAbsent(cle, numeroLigne);
        if (premiereLigne != null && premiereLigne != numeroLigne) {
            return rejet(code, "Code client deja present ligne " + premiereLigne + " du fichier.");
        }

        String raisonSociale = valeur(valeurs, RAISON_SOCIALE);
        if (raisonSociale == null) {
            motifs.add("Raison sociale manquante.");
        }
        String agenceSaisie = valeur(valeurs, AGENCE);
        Agence agence = agenceSaisie == null ? null : contexte.agences().get(normaliserLibelle(agenceSaisie));
        if (agenceSaisie == null) {
            motifs.add("Agence manquante.");
        } else if (agence == null) {
            motifs.add("Agence inconnue : " + agenceSaisie + " (agences connues : " + contexte.listeAgences() + ").");
        } else if (!contexte.dansPerimetre(agence.id())) {
            motifs.add("L'agence " + agence.libelle() + " est hors de votre perimetre.");
        }
        String segmentSaisi = valeur(valeurs, SEGMENTATION);
        String segment = segmentSaisi == null ? null : contexte.segments().get(normaliserLibelle(segmentSaisi));
        if (segmentSaisi != null && segment == null) {
            motifs.add("Segment inconnu : " + segmentSaisi + " (segments connus : " + contexte.listeSegments() + ").");
        }
        Map<String, String> numeros = new LinkedHashMap<>();
        NUMEROS.forEach((fonction, alias) -> {
            String numero = numero(valeurs, alias, LIBELLES_NUMEROS.get(fonction), contexte, motifs);
            if (numero != null) {
                numeros.put(fonction, numero);
            }
        });
        String email = renseigne(valeur(valeurs, EMAIL));
        if (email != null && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            motifs.add("Adresse e-mail invalide : " + email + ".");
        }

        ClientExistant existant = contexte.existants().get(cle);
        if (existant != null && !contexte.dansPerimetre(existant.agenceId())) {
            motifs.add("Ce client existe deja dans une agence hors de votre perimetre.");
        } else if (existant != null && "ARCHIVE".equals(existant.statut())) {
            motifs.add("Ce client est archive : reactivez-le avant de le reimporter.");
        }
        if (!motifs.isEmpty()) {
            return rejet(code, String.join(" ", motifs));
        }

        ClientLu lu = new ClientLu(code, raisonSociale, renseigne(valeur(valeurs, NOM_PHARMACIEN)), agence, valeur(valeurs, SOUS_AGENCE),
                segment, valeur(valeurs, VILLE), valeur(valeurs, ADRESSE), email, renseigne(valeur(valeurs, COMMENTAIRE)), numeros);
        if (existant == null) {
            return new Evaluation(verdict("CREATION", code, null), lu, null);
        }
        List<String> ecarts = ecarts(existant, lu);
        return ecarts.isEmpty()
                ? new Evaluation(verdict("IDENTIQUE", code, null), lu, existant)
                : new Evaluation(verdict("MISE_A_JOUR", code, String.join(" ; ", ecarts)), lu, existant);
    }

    private String numero(Map<String, String> valeurs, List<String> alias, String libelle, Contexte contexte, List<String> motifs) {
        String saisi = renseigne(valeur(valeurs, alias));
        if (saisi == null) {
            return null;
        }
        return ValeursImport.numeroWhatsapp(saisi, contexte.prefixePays()).orElseGet(() -> {
            motifs.add(libelle + " invalide : " + saisi + " (10 chiffres attendus, par exemple 07 00 00 00 01).");
            return null;
        });
    }

    /** Dans les fichiers UBIPHARM, une cellule a 0 signifie « non renseigne ». */
    private String renseigne(String valeur) {
        return valeur == null || valeur.equals("0") ? null : valeur;
    }

    /** Une cellule vide n'efface jamais une valeur existante. */
    private List<String> ecarts(ClientExistant existant, ClientLu lu) {
        List<String> ecarts = new ArrayList<>();
        ecart(ecarts, "Raison sociale", existant.raisonSociale(), lu.raisonSociale());
        ecart(ecarts, "Pharmacien", existant.nomPharmacien(), lu.nomPharmacien());
        ecart(ecarts, "Agence", existant.agenceLibelle(), lu.agence().libelle());
        ecart(ecarts, "Sous-agence", existant.sousAgence(), lu.sousAgence());
        ecart(ecarts, "Segmentation", existant.segmentation(), lu.segmentation());
        ecart(ecarts, "Ville", existant.ville(), lu.ville());
        ecart(ecarts, "Adresse", existant.adresse(), lu.adresse());
        ecart(ecarts, "E-mail", existant.email(), lu.email());
        ecart(ecarts, "Commentaire", existant.commentaire(), lu.commentaire());
        NUMEROS.keySet().forEach(f -> ecart(ecarts, LIBELLES_NUMEROS.get(f), existant.numeros().get(f), lu.numeros().get(f)));
        return ecarts;
    }

    private void ecart(List<String> ecarts, String champ, String actuel, String importe) {
        if (importe != null && !importe.equals(actuel)) {
            ecarts.add(champ + " : " + (actuel == null ? "vide" : actuel) + " -> " + importe);
        }
    }

    private void creer(ClientLu lu) {
        em.createNativeQuery("""
                insert into crm_client (code, raison_sociale, nom_pharmacien, segmentation, agence_id, sous_agence, ville, adresse, email,
                                        commentaire, statut, created_at)
                values (:code, :raisonSociale, :nomPharmacien, :segmentation, :agenceId, :sousAgence, :ville, :adresse, :email,
                        :commentaire, 'ACTIF', utc_timestamp())
                """)
                .setParameter("code", lu.code())
                .setParameter("raisonSociale", lu.raisonSociale())
                .setParameter("nomPharmacien", lu.nomPharmacien())
                .setParameter("segmentation", lu.segmentation())
                .setParameter("agenceId", lu.agence().id())
                .setParameter("sousAgence", lu.sousAgence())
                .setParameter("ville", lu.ville())
                .setParameter("adresse", lu.adresse())
                .setParameter("email", lu.email())
                .setParameter("commentaire", lu.commentaire())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        lu.numeros().forEach((fonction, numero) -> ajouterContact(id, fonction, numero, nomContact(fonction, lu)));
    }

    private void mettreAJour(ClientExistant existant, ClientLu lu) {
        em.createNativeQuery("""
                update crm_client set raison_sociale = :raisonSociale, agence_id = :agenceId,
                       nom_pharmacien = coalesce(:nomPharmacien, nom_pharmacien),
                       sous_agence = coalesce(:sousAgence, sous_agence), segmentation = coalesce(:segmentation, segmentation),
                       ville = coalesce(:ville, ville), adresse = coalesce(:adresse, adresse), email = coalesce(:email, email),
                       commentaire = coalesce(:commentaire, commentaire), updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("raisonSociale", lu.raisonSociale())
                .setParameter("agenceId", lu.agence().id())
                .setParameter("nomPharmacien", lu.nomPharmacien())
                .setParameter("sousAgence", lu.sousAgence())
                .setParameter("segmentation", lu.segmentation())
                .setParameter("ville", lu.ville())
                .setParameter("adresse", lu.adresse())
                .setParameter("email", lu.email())
                .setParameter("commentaire", lu.commentaire())
                .setParameter("id", existant.id())
                .executeUpdate();
        lu.numeros().forEach((fonction, numero) -> remplacerContact(existant.id(), fonction, existant.numeros().get(fonction), numero, nomContact(fonction, lu)));
    }

    /** L'ancien numero est archive, pas supprime : l'historique des envois y reste rattache. */
    private void remplacerContact(Long clientId, String fonction, String actuel, String nouveau, String nom) {
        if (nouveau == null || nouveau.equals(actuel)) {
            return;
        }
        em.createNativeQuery("""
                update crm_contact set statut = 'ARCHIVE', updated_at = utc_timestamp()
                where client_id = :clientId and fonction = :fonction and statut = 'ACTIF'
                """)
                .setParameter("clientId", clientId)
                .setParameter("fonction", fonction)
                .executeUpdate();
        ajouterContact(clientId, fonction, nouveau, nom);
    }

    /** Le nom du pharmacien est celui du titulaire. */
    private String nomContact(String fonction, ClientLu lu) {
        return "TITULAIRE".equals(fonction) ? lu.nomPharmacien() : null;
    }

    /** Rang 1 : numero de relance ; rang 2 : autres interlocuteurs de la pharmacie. */
    private void ajouterContact(Long clientId, String fonction, String numero, String nom) {
        em.createNativeQuery("""
                insert into crm_contact (client_id, nom, fonction, numero_whatsapp, rang, statut, created_at)
                values (:clientId, :nom, :fonction, :numero, :rang, 'ACTIF', utc_timestamp())
                """)
                .setParameter("clientId", clientId)
                .setParameter("nom", nom)
                .setParameter("fonction", fonction)
                .setParameter("numero", numero)
                .setParameter("rang", "PRINCIPAL".equals(fonction) ? 1 : 2)
                .executeUpdate();
    }

    private Contexte contexte(List<Map<String, String>> lignes) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        @SuppressWarnings("unchecked")
        List<Tuple> agences = em.createNativeQuery("select id as id, code as code, code_import as codeImport, libelle as libelle from crm_agence where statut = 'ACTIF' order by libelle", Tuple.class)
                .getResultList();
        Map<String, Agence> agencesParCle = new HashMap<>();
        for (Tuple t : agences) {
            Agence agence = new Agence(t.get("id", Long.class), t.get("libelle", String.class));
            agencesParCle.put(normaliserLibelle(t.get("code", String.class)), agence);
            agencesParCle.putIfAbsent(normaliserLibelle(agence.libelle()), agence);
            if (t.get("codeImport", String.class) != null) {
                agencesParCle.putIfAbsent(normaliserLibelle(t.get("codeImport", String.class)), agence);
            }
        }
        String listeAgences = agences.stream().map(t -> (t.get("codeImport", String.class) == null ? t.get("code", String.class) : t.get("codeImport", String.class))
                + " " + t.get("libelle", String.class))
                .collect(Collectors.joining(", "));

        @SuppressWarnings("unchecked")
        List<Tuple> segments = em.createNativeQuery("select code as code, libelle as libelle from crm_segment where statut = 'ACTIF' order by ordre", Tuple.class)
                .getResultList();
        Map<String, String> segmentsParCle = new HashMap<>();
        for (Tuple t : segments) {
            segmentsParCle.put(normaliserLibelle(t.get("code", String.class)), t.get("code", String.class));
            segmentsParCle.putIfAbsent(normaliserLibelle(t.get("libelle", String.class)), t.get("code", String.class));
        }
        String listeSegments = segments.stream().map(t -> t.get("code", String.class) + " " + t.get("libelle", String.class))
                .collect(Collectors.joining(", "));

        Set<String> codes = new HashSet<>();
        lignes.forEach(v -> {
            String code = valeur(v, CODE);
            if (code != null) {
                codes.add(code.toUpperCase(Locale.ROOT));
            }
        });
        return new Contexte(perimetre, agencesParCle, listeAgences, segmentsParCle, listeSegments, chargerExistants(codes),
                parametreService.valeur("whatsapp.prefixe_pays", "225"), new HashMap<>());
    }

    private Map<String, ClientExistant> chargerExistants(Set<String> codes) {
        Map<String, ClientExistant> existants = new HashMap<>();
        List<String> liste = new ArrayList<>(codes);
        for (int debut = 0; debut < liste.size(); debut += TAILLE_LOT) {
            List<String> lot = liste.subList(debut, Math.min(debut + TAILLE_LOT, liste.size()));
            @SuppressWarnings("unchecked")
            List<Tuple> clients = em.createNativeQuery("""
                    select c.id as id, c.code as code, c.raison_sociale as raisonSociale, c.nom_pharmacien as nomPharmacien,
                           c.agence_id as agenceId,
                           a.libelle as agenceLibelle, c.sous_agence as sousAgence, c.segmentation as segmentation,
                           c.ville as ville, c.adresse as adresse, c.email as email, c.commentaire as commentaire,
                           c.statut as statut
                    from crm_client c join crm_agence a on a.id = c.agence_id
                    where c.code in (:codes)
                    """, Tuple.class).setParameter("codes", lot).getResultList();
            @SuppressWarnings("unchecked")
            List<Tuple> contacts = em.createNativeQuery("""
                    select ct.client_id as clientId, ct.fonction as fonction, ct.numero_whatsapp as numero
                    from crm_contact ct join crm_client c on c.id = ct.client_id
                    where c.code in (:codes) and ct.statut = 'ACTIF'
                    order by ct.id
                    """, Tuple.class).setParameter("codes", lot).getResultList();
            Map<Long, Map<String, String>> numerosParClient = new HashMap<>();
            for (Tuple t : contacts) {
                numerosParClient.computeIfAbsent(t.get("clientId", Long.class), k -> new HashMap<>())
                        .put(t.get("fonction", String.class), t.get("numero", String.class));
            }
            for (Tuple t : clients) {
                ClientExistant client = mapTupleToExistant(t, numerosParClient.getOrDefault(t.get("id", Long.class), Map.of()));
                existants.put(client.code().toUpperCase(Locale.ROOT), client);
            }
        }
        return existants;
    }

    ClientExistant mapTupleToExistant(Tuple tuple, Map<String, String> numeros) {
        return new ClientExistant(
                tuple.get("id", Long.class),
                tuple.get("code", String.class),
                tuple.get("raisonSociale", String.class),
                tuple.get("nomPharmacien", String.class),
                tuple.get("agenceId", Long.class),
                tuple.get("agenceLibelle", String.class),
                tuple.get("sousAgence", String.class),
                tuple.get("segmentation", String.class),
                tuple.get("ville", String.class),
                tuple.get("adresse", String.class),
                tuple.get("email", String.class),
                tuple.get("commentaire", String.class),
                tuple.get("statut", String.class),
                numeros);
    }

    private Evaluation rejet(String cle, String message) {
        return new Evaluation(verdict("REJET", cle, message), null, null);
    }

    private ResultatLigneDTO verdict(String action, String cle, String message) {
        return ResultatLigneDTO.builder().action(action).cle(cle).message(message).build();
    }

    record Agence(Long id, String libelle) {
    }

    record ClientLu(String code, String raisonSociale, String nomPharmacien, Agence agence, String sousAgence, String segmentation, String ville,
            String adresse, String email, String commentaire, Map<String, String> numeros) {
    }

    record ClientExistant(Long id, String code, String raisonSociale, String nomPharmacien, Long agenceId, String agenceLibelle, String sousAgence,
            String segmentation, String ville, String adresse, String email, String commentaire, String statut,
            Map<String, String> numeros) {
    }

    record Evaluation(ResultatLigneDTO verdict, ClientLu lu, ClientExistant existant) {
    }

    /** Referentiels charges une fois par import ; lignesVues detecte les doublons dans le fichier. */
    record Contexte(PerimetreDTO perimetre, Map<String, Agence> agences, String listeAgences,
            Map<String, String> segments, String listeSegments,
            Map<String, ClientExistant> existants, String prefixePays, Map<String, Integer> lignesVues) {

        boolean dansPerimetre(Long agenceId) {
            return perimetre.isVisionGlobale() || perimetre.getAgenceIds().stream().anyMatch(a -> Objects.equals(a, agenceId));
        }
    }
}
