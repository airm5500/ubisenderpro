package ci.ubipharm.crm.commun.rest.service.impl;

import static ci.ubipharm.crm.commun.rest.service.impl.ValeursImport.colonnePresente;
import static ci.ubipharm.crm.commun.rest.service.impl.ValeursImport.normaliserLibelle;
import static ci.ubipharm.crm.commun.rest.service.impl.ValeursImport.valeur;

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

import ci.ubipharm.crm.commun.rest.service.ImportClientService;
import ci.ubipharm.crm.commun.rest.service.ImportService;
import ci.ubipharm.crm.commun.rest.service.LecteurTableurService;
import ci.ubipharm.crm.commun.rest.service.ParametreService;
import ci.ubipharm.crm.commun.rest.service.dto.ImportDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ImportLigneDTO;
import ci.ubipharm.crm.commun.rest.service.dto.LigneTableurDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ResultatLigneDTO;
import ci.ubipharm.crm.commun.rest.service.dto.TableurDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ValidationImportDTO;
import ci.ubipharm.crm.securite.rest.service.PerimetreService;
import ci.ubipharm.crm.securite.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
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

    static final List<String> CODE = List.of("code_client", "code", "client_code", "numero_client", "n_client");
    static final List<String> RAISON_SOCIALE = List.of("raison_sociale", "nom_client", "nom", "pharmacie", "libelle");
    static final List<String> AGENCE = List.of("agence", "code_agence", "agence_code", "libelle_agence");
    static final List<String> SEGMENTATION = List.of("segmentation", "segment", "categorie");
    static final List<String> VILLE = List.of("ville", "localite", "commune");
    static final List<String> NUMERO_1 = List.of("numero_1", "numero_whatsapp", "numero_principal", "whatsapp", "contact_1",
            "telephone", "telephone_1", "tel", "tel_1");
    static final List<String> NOM_1 = List.of("nom_contact_1", "nom_contact", "titulaire");
    static final List<String> NUMERO_2 = List.of("numero_2", "numero_secondaire", "contact_2", "telephone_2", "tel_2");
    static final List<String> NOM_2 = List.of("nom_contact_2", "employe");

    private static final int TAILLE_LOT = 1000;

    private final EntityManager em;
    private final LecteurTableurService lecteurTableurService;
    private final ImportService importService;
    private final PerimetreService perimetreService;
    private final ParametreService parametreService;

    @Override
    @Transactional
    public ImportDTO simuler(MultipartFile fichier) {
        TableurDTO tableur = lecteurTableurService.lire(fichier);
        verifierColonnes(tableur.getEntetes());
        Contexte contexte = contexte(tableur.getLignes().stream().map(LigneTableurDTO::getValeurs).toList());

        List<ImportLigneDTO> lignes = new ArrayList<>();
        for (LigneTableurDTO ligne : tableur.getLignes()) {
            ResultatLigneDTO verdict = evaluer(ligne.getNumero(), ligne.getValeurs(), contexte).verdict();
            lignes.add(ImportLigneDTO.builder()
                    .numeroLigne(ligne.getNumero())
                    .cle(verdict.getCle())
                    .action(verdict.getAction())
                    .message(verdict.getMessage())
                    .donnees(ligne.getValeurs())
                    .build());
        }
        return importService.simulation(TYPE, fichier.getOriginalFilename(), lignes);
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

    private void verifierColonnes(List<String> entetes) {
        List<String> manquantes = new ArrayList<>();
        if (!colonnePresente(entetes, CODE)) {
            manquantes.add("code client");
        }
        if (!colonnePresente(entetes, RAISON_SOCIALE)) {
            manquantes.add("raison sociale");
        }
        if (!colonnePresente(entetes, AGENCE)) {
            manquantes.add("agence");
        }
        if (!manquantes.isEmpty()) {
            throw new ErreurMetierException("Colonnes obligatoires absentes : " + String.join(", ", manquantes)
                    + ". Colonnes attendues : code client, raison sociale, agence, et en option segmentation, ville, numero 1, numero 2.");
        }
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
        String numero1 = numero(valeurs, NUMERO_1, "principal", contexte, motifs);
        String numero2 = numero(valeurs, NUMERO_2, "secondaire", contexte, motifs);

        ClientExistant existant = contexte.existants().get(cle);
        if (existant != null && !contexte.dansPerimetre(existant.agenceId())) {
            motifs.add("Ce client existe deja dans une agence hors de votre perimetre.");
        } else if (existant != null && "ARCHIVE".equals(existant.statut())) {
            motifs.add("Ce client est archive : reactivez-le avant de le reimporter.");
        }
        if (!motifs.isEmpty()) {
            return rejet(code, String.join(" ", motifs));
        }

        ClientLu lu = new ClientLu(code, raisonSociale, agence, valeur(valeurs, SEGMENTATION), valeur(valeurs, VILLE),
                numero1, valeur(valeurs, NOM_1), numero2, valeur(valeurs, NOM_2));
        if (existant == null) {
            return new Evaluation(verdict("CREATION", code, null), lu, null);
        }
        List<String> ecarts = ecarts(existant, lu);
        return ecarts.isEmpty()
                ? new Evaluation(verdict("IDENTIQUE", code, null), lu, existant)
                : new Evaluation(verdict("MISE_A_JOUR", code, String.join(" ; ", ecarts)), lu, existant);
    }

    private String numero(Map<String, String> valeurs, List<String> alias, String libelle, Contexte contexte, List<String> motifs) {
        String saisi = valeur(valeurs, alias);
        if (saisi == null) {
            return null;
        }
        return ValeursImport.numeroWhatsapp(saisi, contexte.prefixePays()).orElseGet(() -> {
            motifs.add("Numero " + libelle + " invalide : " + saisi + " (10 chiffres attendus, par exemple 07 00 00 00 01).");
            return null;
        });
    }

    /** Une cellule vide n'efface jamais une valeur existante. */
    private List<String> ecarts(ClientExistant existant, ClientLu lu) {
        List<String> ecarts = new ArrayList<>();
        ecart(ecarts, "Raison sociale", existant.raisonSociale(), lu.raisonSociale());
        ecart(ecarts, "Agence", existant.agenceLibelle(), lu.agence().libelle());
        ecart(ecarts, "Segmentation", existant.segmentation(), lu.segmentation());
        ecart(ecarts, "Ville", existant.ville(), lu.ville());
        ecart(ecarts, "Numero principal", existant.numero1(), lu.numero1());
        ecart(ecarts, "Numero secondaire", existant.numero2(), lu.numero2());
        return ecarts;
    }

    private void ecart(List<String> ecarts, String champ, String actuel, String importe) {
        if (importe != null && !importe.equals(actuel)) {
            ecarts.add(champ + " : " + (actuel == null ? "vide" : actuel) + " -> " + importe);
        }
    }

    private void creer(ClientLu lu) {
        em.createNativeQuery("""
                insert into crm_client (code, raison_sociale, segmentation, agence_id, ville, statut, created_at)
                values (:code, :raisonSociale, :segmentation, :agenceId, :ville, 'ACTIF', utc_timestamp())
                """)
                .setParameter("code", lu.code())
                .setParameter("raisonSociale", lu.raisonSociale())
                .setParameter("segmentation", lu.segmentation())
                .setParameter("agenceId", lu.agence().id())
                .setParameter("ville", lu.ville())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        ajouterContact(id, 1, lu.numero1(), lu.nom1());
        ajouterContact(id, 2, lu.numero2(), lu.nom2());
    }

    private void mettreAJour(ClientExistant existant, ClientLu lu) {
        em.createNativeQuery("""
                update crm_client set raison_sociale = :raisonSociale, agence_id = :agenceId,
                       segmentation = coalesce(:segmentation, segmentation), ville = coalesce(:ville, ville),
                       updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("raisonSociale", lu.raisonSociale())
                .setParameter("agenceId", lu.agence().id())
                .setParameter("segmentation", lu.segmentation())
                .setParameter("ville", lu.ville())
                .setParameter("id", existant.id())
                .executeUpdate();
        remplacerContact(existant.id(), 1, existant.numero1(), lu.numero1(), lu.nom1());
        remplacerContact(existant.id(), 2, existant.numero2(), lu.numero2(), lu.nom2());
    }

    /** L'ancien numero est archive, pas supprime : l'historique des envois y reste rattache. */
    private void remplacerContact(Long clientId, int rang, String actuel, String nouveau, String nom) {
        if (nouveau == null || nouveau.equals(actuel)) {
            return;
        }
        em.createNativeQuery("""
                update crm_contact set statut = 'ARCHIVE', updated_at = utc_timestamp()
                where client_id = :clientId and rang = :rang and statut = 'ACTIF'
                """)
                .setParameter("clientId", clientId)
                .setParameter("rang", rang)
                .executeUpdate();
        ajouterContact(clientId, rang, nouveau, nom);
    }

    private void ajouterContact(Long clientId, int rang, String numero, String nom) {
        if (numero == null) {
            return;
        }
        em.createNativeQuery("""
                insert into crm_contact (client_id, nom, numero_whatsapp, rang, statut, created_at)
                values (:clientId, :nom, :numero, :rang, 'ACTIF', utc_timestamp())
                """)
                .setParameter("clientId", clientId)
                .setParameter("nom", nom)
                .setParameter("numero", numero)
                .setParameter("rang", rang)
                .executeUpdate();
    }

    private Contexte contexte(List<Map<String, String>> lignes) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        @SuppressWarnings("unchecked")
        List<Tuple> agences = em.createNativeQuery("select id as id, code as code, libelle as libelle from crm_agence where statut = 'ACTIF' order by libelle", Tuple.class)
                .getResultList();
        Map<String, Agence> agencesParCle = new HashMap<>();
        for (Tuple t : agences) {
            Agence agence = new Agence(t.get("id", Long.class), t.get("libelle", String.class));
            agencesParCle.put(normaliserLibelle(t.get("code", String.class)), agence);
            agencesParCle.putIfAbsent(normaliserLibelle(agence.libelle()), agence);
        }
        String listeAgences = agences.stream().map(t -> t.get("code", String.class) + " " + t.get("libelle", String.class))
                .collect(Collectors.joining(", "));

        Set<String> codes = new HashSet<>();
        lignes.forEach(v -> {
            String code = valeur(v, CODE);
            if (code != null) {
                codes.add(code.toUpperCase(Locale.ROOT));
            }
        });
        return new Contexte(perimetre, agencesParCle, listeAgences, chargerExistants(codes),
                parametreService.valeur("whatsapp.prefixe_pays", "225"), new HashMap<>());
    }

    private Map<String, ClientExistant> chargerExistants(Set<String> codes) {
        Map<String, ClientExistant> existants = new HashMap<>();
        List<String> liste = new ArrayList<>(codes);
        for (int debut = 0; debut < liste.size(); debut += TAILLE_LOT) {
            @SuppressWarnings("unchecked")
            List<Tuple> lignes = em.createNativeQuery("""
                    select c.id as id, c.code as code, c.raison_sociale as raisonSociale, c.agence_id as agenceId,
                           a.libelle as agenceLibelle, c.segmentation as segmentation, c.ville as ville, c.statut as statut,
                           (select ct.numero_whatsapp from crm_contact ct
                             where ct.client_id = c.id and ct.rang = 1 and ct.statut = 'ACTIF' limit 1) as numero1,
                           (select ct.numero_whatsapp from crm_contact ct
                             where ct.client_id = c.id and ct.rang = 2 and ct.statut = 'ACTIF' limit 1) as numero2
                    from crm_client c join crm_agence a on a.id = c.agence_id
                    where c.code in (:codes)
                    """, Tuple.class)
                    .setParameter("codes", liste.subList(debut, Math.min(debut + TAILLE_LOT, liste.size())))
                    .getResultList();
            for (Tuple t : lignes) {
                ClientExistant client = mapTupleToExistant(t);
                existants.put(client.code().toUpperCase(Locale.ROOT), client);
            }
        }
        return existants;
    }

    ClientExistant mapTupleToExistant(Tuple tuple) {
        return new ClientExistant(
                tuple.get("id", Long.class),
                tuple.get("code", String.class),
                tuple.get("raisonSociale", String.class),
                tuple.get("agenceId", Long.class),
                tuple.get("agenceLibelle", String.class),
                tuple.get("segmentation", String.class),
                tuple.get("ville", String.class),
                tuple.get("statut", String.class),
                tuple.get("numero1", String.class),
                tuple.get("numero2", String.class));
    }

    private Evaluation rejet(String cle, String message) {
        return new Evaluation(verdict("REJET", cle, message), null, null);
    }

    private ResultatLigneDTO verdict(String action, String cle, String message) {
        return ResultatLigneDTO.builder().action(action).cle(cle).message(message).build();
    }

    record Agence(Long id, String libelle) {
    }

    record ClientLu(String code, String raisonSociale, Agence agence, String segmentation, String ville,
            String numero1, String nom1, String numero2, String nom2) {
    }

    record ClientExistant(Long id, String code, String raisonSociale, Long agenceId, String agenceLibelle,
            String segmentation, String ville, String statut, String numero1, String numero2) {
    }

    record Evaluation(ResultatLigneDTO verdict, ClientLu lu, ClientExistant existant) {
    }

    /** Referentiels charges une fois par import ; lignesVues detecte les doublons dans le fichier. */
    record Contexte(PerimetreDTO perimetre, Map<String, Agence> agences, String listeAgences,
            Map<String, ClientExistant> existants, String prefixePays, Map<String, Integer> lignesVues) {

        boolean dansPerimetre(Long agenceId) {
            return perimetre.isVisionGlobale() || perimetre.getAgenceIds().stream().anyMatch(a -> Objects.equals(a, agenceId));
        }
    }
}
