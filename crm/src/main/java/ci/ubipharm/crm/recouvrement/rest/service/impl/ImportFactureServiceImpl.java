package ci.ubipharm.crm.recouvrement.rest.service.impl;

import static ci.ubipharm.crm.commun.rest.service.impl.ValeursImport.champ;
import static ci.ubipharm.crm.commun.rest.service.impl.ValeursImport.montantLisible;
import static ci.ubipharm.crm.commun.rest.service.impl.ValeursImport.normaliserLibelle;
import static ci.ubipharm.crm.commun.rest.service.impl.ValeursImport.valeur;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.commun.rest.service.CorrespondanceImportService;
import ci.ubipharm.crm.commun.rest.service.ImportService;
import ci.ubipharm.crm.commun.rest.service.LecteurTableurService;
import ci.ubipharm.crm.commun.rest.service.dto.ChampImportDTO;
import ci.ubipharm.crm.commun.rest.service.dto.CorrectionLigneDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ImportDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ImportLigneDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ParametresImportDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ResultatLigneDTO;
import ci.ubipharm.crm.commun.rest.service.dto.TableurDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ValidationImportDTO;
import ci.ubipharm.crm.commun.rest.service.impl.ValeursImport;
import ci.ubipharm.crm.recouvrement.rest.service.ImportFactureService;
import ci.ubipharm.crm.securite.rest.service.PerimetreService;
import ci.ubipharm.crm.securite.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

/**
 * Import des factures et avoirs. Le solde du fichier fait foi ; une facture deja connue n'est mise a jour
 * que si l'utilisateur le choisit ; une ligne anormale est rejetee avec son motif, sans bloquer les autres.
 */
@Service
@RequiredArgsConstructor
public class ImportFactureServiceImpl implements ImportFactureService {

    static final String TYPE = "FACTURES";

    static final List<String> REFERENCE = List.of("reference", "numero_facture", "num_facture", "n_facture", "no_facture",
            "ref_facture", "reference_facture", "facture", "piece", "numero_piece");
    static final List<String> CODE_CLIENT = List.of("code_client", "client_code", "client", "code", "numero_client");
    static final List<String> DATE_FACTURE = List.of("date_facture", "date", "date_emission", "date_piece");
    static final List<String> DATE_ECHEANCE = List.of("date_echeance", "echeance", "date_d_echeance", "date_limite");
    static final List<String> MONTANT = List.of("montant", "montant_ttc", "montant_facture", "montant_initial");
    static final List<String> SOLDE = List.of("solde", "reste_a_payer", "solde_restant", "montant_restant", "restant_du", "reste_du");
    static final List<String> SOUS_TYPE = List.of("sous_type", "sous_type_compte", "type_compte", "type_de_compte");
    static final List<String> NATURE = List.of("nature", "type_piece", "type");

    /** Champs proposes par l'assistant, dans l'ordre de l'exemplaire. */
    static final List<ChampImportDTO> CHAMPS = List.of(
            champ("Reference facture", true, "FA-2026-000123", REFERENCE),
            champ("Numero de compte client", true, "5125", CODE_CLIENT),
            champ("Date facture", true, "01/09/2026", DATE_FACTURE),
            champ("Date d'echeance", true, "30/09/2026", DATE_ECHEANCE),
            champ("Montant", true, "150000", MONTANT),
            champ("Solde", true, "100000", SOLDE),
            champ("Sous-type", false, "Normal", SOUS_TYPE),
            champ("Nature", false, "Facture", NATURE));

    private static final String SOUS_TYPE_PAR_DEFAUT = "NORMAL";
    private static final DateTimeFormatter DATE_LISIBLE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
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
        return "RECOUVREMENT";
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
                    creer(evaluation.lue(), id);
                    ligne.setResultat("CREE");
                }
                case "MISE_A_JOUR" -> {
                    if (choix.isToutesLesMisesAJour() || clesChoisies.contains(verdict.getCle().toUpperCase(Locale.ROOT))) {
                        mettreAJour(evaluation.existante(), evaluation.lue(), id);
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
        String reference = valeur(valeurs, REFERENCE);
        if (reference == null) {
            return rejet(null, "Reference de facture manquante.");
        }
        String cle = reference.toUpperCase(Locale.ROOT);
        Integer premiereLigne = contexte.lignesVues().putIfAbsent(cle, numeroLigne);
        if (premiereLigne != null && premiereLigne != numeroLigne) {
            return rejet(reference, "Reference deja presente ligne " + premiereLigne + " du fichier.");
        }

        List<String> motifs = new ArrayList<>();
        String codeClient = valeur(valeurs, CODE_CLIENT);
        Client client = codeClient == null ? null : contexte.clients().get(codeClient.toUpperCase(Locale.ROOT));
        if (codeClient == null) {
            motifs.add("Code client manquant.");
        } else if (client == null) {
            motifs.add("Client inconnu : " + codeClient + ". Importez d'abord le client.");
        } else if (!contexte.dansPerimetre(client.agenceId())) {
            motifs.add("Le client " + codeClient + " est hors de votre perimetre.");
        } else if ("ARCHIVE".equals(client.statut())) {
            motifs.add("Le client " + codeClient + " est archive.");
        }

        LocalDate dateFacture = date(valeurs, DATE_FACTURE, "Date de facture", motifs);
        LocalDate dateEcheance = date(valeurs, DATE_ECHEANCE, "Date d'echeance", motifs);
        if (dateFacture != null && dateEcheance != null && dateEcheance.isBefore(dateFacture)) {
            motifs.add("L'echeance (" + dateEcheance.format(DATE_LISIBLE) + ") precede la date de facture ("
                    + dateFacture.format(DATE_LISIBLE) + ").");
        }
        BigDecimal montant = montant(valeurs, MONTANT, "Montant", motifs);
        BigDecimal solde = montant(valeurs, SOLDE, "Solde", motifs);
        if (montant != null && solde != null && solde.abs().compareTo(montant.abs()) > 0) {
            motifs.add("Le solde (" + montantLisible(solde) + ") depasse le montant (" + montantLisible(montant) + ").");
        }

        String sousTypeSaisi = valeur(valeurs, SOUS_TYPE);
        SousType sousType = contexte.sousTypes().get(normaliserLibelle(sousTypeSaisi == null ? SOUS_TYPE_PAR_DEFAUT : sousTypeSaisi));
        if (sousType == null) {
            motifs.add("Sous-type inconnu : " + sousTypeSaisi + " (sous-types connus : " + contexte.listeSousTypes() + ").");
        }

        FactureExistante existante = contexte.existantes().get(cle);
        if (existante != null && client != null && !existante.clientId().equals(client.id())) {
            motifs.add("Cette reference appartient deja au client " + existante.clientCode() + ".");
        }
        if (!motifs.isEmpty()) {
            return rejet(reference, String.join(" ", motifs));
        }

        String natureSaisie = valeur(valeurs, NATURE);
        boolean avoir = (natureSaisie != null && normaliserLibelle(natureSaisie).contains("avoir")) || montant.signum() < 0;
        FactureLue lue = new FactureLue(reference, client, sousType, avoir ? "AVOIR" : "FACTURE", dateFacture, dateEcheance, montant, solde);
        if (existante == null) {
            return new Evaluation(verdict("CREATION", reference, null), lue, null);
        }
        List<String> ecarts = ecarts(existante, lue);
        return ecarts.isEmpty()
                ? new Evaluation(verdict("IDENTIQUE", reference, null), lue, existante)
                : new Evaluation(verdict("MISE_A_JOUR", reference, String.join(" ; ", ecarts)), lue, existante);
    }

    private LocalDate date(Map<String, String> valeurs, List<String> alias, String libelle, List<String> motifs) {
        String saisie = valeur(valeurs, alias);
        if (saisie == null) {
            motifs.add(libelle + " manquante.");
            return null;
        }
        return ValeursImport.date(saisie).orElseGet(() -> {
            motifs.add(libelle + " illisible : " + saisie + " (format attendu JJ/MM/AAAA).");
            return null;
        });
    }

    private BigDecimal montant(Map<String, String> valeurs, List<String> alias, String libelle, List<String> motifs) {
        String saisi = valeur(valeurs, alias);
        if (saisi == null) {
            motifs.add(libelle + " manquant.");
            return null;
        }
        return ValeursImport.montant(saisi).map(m -> m.setScale(2, RoundingMode.HALF_UP)).orElseGet(() -> {
            motifs.add(libelle + " illisible : " + saisi + ".");
            return null;
        });
    }

    private List<String> ecarts(FactureExistante existante, FactureLue lue) {
        List<String> ecarts = new ArrayList<>();
        ecart(ecarts, "Solde", existante.solde(), lue.solde(), ValeursImport::montantLisible);
        ecart(ecarts, "Montant", existante.montant(), lue.montant(), ValeursImport::montantLisible);
        ecart(ecarts, "Echeance", existante.dateEcheance(), lue.dateEcheance(), d -> d.format(DATE_LISIBLE));
        ecart(ecarts, "Date de facture", existante.dateFacture(), lue.dateFacture(), d -> d.format(DATE_LISIBLE));
        ecart(ecarts, "Sous-type", existante.sousTypeLibelle(), lue.sousType().libelle(), Function.identity());
        ecart(ecarts, "Nature", existante.nature(), lue.nature(), Function.identity());
        return ecarts;
    }

    private <T> void ecart(List<String> ecarts, String champ, T actuel, T importe, Function<T, String> format) {
        boolean different = actuel instanceof BigDecimal a && importe instanceof BigDecimal b ? a.compareTo(b) != 0 : !Objects.equals(actuel, importe);
        if (different) {
            ecarts.add(champ + " : " + format.apply(actuel) + " -> " + format.apply(importe));
        }
    }

    private void creer(FactureLue lue, Long importId) {
        em.createNativeQuery("""
                insert into crm_facture (reference, client_id, sous_type_id, nature, date_facture, date_echeance,
                                         montant, solde, statut, import_id, created_at)
                values (:reference, :clientId, :sousTypeId, :nature, :dateFacture, :dateEcheance, :montant, :solde,
                        case when :solde = 0 then 'SOLDEE' else 'A_RELANCER' end, :importId, utc_timestamp())
                """)
                .setParameter("reference", lue.reference())
                .setParameter("clientId", lue.client().id())
                .setParameter("sousTypeId", lue.sousType().id())
                .setParameter("nature", lue.nature())
                .setParameter("dateFacture", lue.dateFacture())
                .setParameter("dateEcheance", lue.dateEcheance())
                .setParameter("montant", lue.montant())
                .setParameter("solde", lue.solde())
                .setParameter("importId", importId)
                .executeUpdate();
    }

    /** Un solde a zero solde la facture ; une facture soldee dont le solde reapparait repasse a relancer. */
    private void mettreAJour(FactureExistante existante, FactureLue lue, Long importId) {
        em.createNativeQuery("""
                update crm_facture set statut = case when :solde = 0 then 'SOLDEE'
                                                     when statut = 'SOLDEE' then 'A_RELANCER'
                                                     else statut end,
                       sous_type_id = :sousTypeId, nature = :nature, date_facture = :dateFacture,
                       date_echeance = :dateEcheance, montant = :montant, solde = :solde,
                       import_id = :importId, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("sousTypeId", lue.sousType().id())
                .setParameter("nature", lue.nature())
                .setParameter("dateFacture", lue.dateFacture())
                .setParameter("dateEcheance", lue.dateEcheance())
                .setParameter("montant", lue.montant())
                .setParameter("solde", lue.solde())
                .setParameter("importId", importId)
                .setParameter("id", existante.id())
                .executeUpdate();
    }

    private Contexte contexte(List<Map<String, String>> lignes) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        @SuppressWarnings("unchecked")
        List<Tuple> sousTypes = em.createNativeQuery(
                "select id as id, code as code, libelle as libelle from crm_sous_type_compte where statut = 'ACTIF' order by libelle",
                Tuple.class).getResultList();
        Map<String, SousType> sousTypesParCle = new HashMap<>();
        for (Tuple t : sousTypes) {
            SousType sousType = new SousType(t.get("id", Long.class), t.get("libelle", String.class));
            sousTypesParCle.put(normaliserLibelle(t.get("code", String.class)), sousType);
            sousTypesParCle.putIfAbsent(normaliserLibelle(sousType.libelle()), sousType);
        }
        String listeSousTypes = sousTypes.stream().map(t -> t.get("libelle", String.class)).collect(Collectors.joining(", "));

        Set<String> references = new HashSet<>();
        Set<String> codesClients = new HashSet<>();
        lignes.forEach(v -> {
            String reference = valeur(v, REFERENCE);
            String code = valeur(v, CODE_CLIENT);
            if (reference != null) {
                references.add(reference.toUpperCase(Locale.ROOT));
            }
            if (code != null) {
                codesClients.add(code.toUpperCase(Locale.ROOT));
            }
        });
        return new Contexte(perimetre, sousTypesParCle, listeSousTypes, chargerClients(codesClients),
                chargerExistantes(references), new HashMap<>());
    }

    private Map<String, Client> chargerClients(Set<String> codes) {
        Map<String, Client> clients = new HashMap<>();
        for (List<String> lot : lots(codes)) {
            @SuppressWarnings("unchecked")
            List<Tuple> lignes = em.createNativeQuery(
                    "select id as id, code as code, agence_id as agenceId, statut as statut from crm_client where code in (:codes)",
                    Tuple.class).setParameter("codes", lot).getResultList();
            for (Tuple t : lignes) {
                Client client = new Client(t.get("id", Long.class), t.get("code", String.class), t.get("agenceId", Long.class),
                        t.get("statut", String.class));
                clients.put(client.code().toUpperCase(Locale.ROOT), client);
            }
        }
        return clients;
    }

    private Map<String, FactureExistante> chargerExistantes(Set<String> references) {
        Map<String, FactureExistante> existantes = new HashMap<>();
        for (List<String> lot : lots(references)) {
            @SuppressWarnings("unchecked")
            List<Tuple> lignes = em.createNativeQuery("""
                    select f.id as id, f.reference as reference, f.client_id as clientId, c.code as clientCode,
                           st.libelle as sousTypeLibelle, f.nature as nature, f.date_facture as dateFacture,
                           f.date_echeance as dateEcheance, f.montant as montant, f.solde as solde
                    from crm_facture f
                    join crm_client c on c.id = f.client_id
                    join crm_sous_type_compte st on st.id = f.sous_type_id
                    where f.reference in (:references)
                    """, Tuple.class).setParameter("references", lot).getResultList();
            for (Tuple t : lignes) {
                FactureExistante facture = mapTupleToExistante(t);
                existantes.put(facture.reference().toUpperCase(Locale.ROOT), facture);
            }
        }
        return existantes;
    }

    FactureExistante mapTupleToExistante(Tuple tuple) {
        return new FactureExistante(
                tuple.get("id", Long.class),
                tuple.get("reference", String.class),
                tuple.get("clientId", Long.class),
                tuple.get("clientCode", String.class),
                tuple.get("sousTypeLibelle", String.class),
                tuple.get("nature", String.class),
                tuple.get("dateFacture", LocalDate.class),
                tuple.get("dateEcheance", LocalDate.class),
                tuple.get("montant", BigDecimal.class),
                tuple.get("solde", BigDecimal.class));
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
        return new Evaluation(verdict("REJET", cle, message), null, null);
    }

    private ResultatLigneDTO verdict(String action, String cle, String message) {
        return ResultatLigneDTO.builder().action(action).cle(cle).message(message).build();
    }

    record Client(Long id, String code, Long agenceId, String statut) {
    }

    record SousType(Long id, String libelle) {
    }

    record FactureLue(String reference, Client client, SousType sousType, String nature, LocalDate dateFacture,
            LocalDate dateEcheance, BigDecimal montant, BigDecimal solde) {
    }

    record FactureExistante(Long id, String reference, Long clientId, String clientCode, String sousTypeLibelle, String nature,
            LocalDate dateFacture, LocalDate dateEcheance, BigDecimal montant, BigDecimal solde) {
    }

    record Evaluation(ResultatLigneDTO verdict, FactureLue lue, FactureExistante existante) {
    }

    /** Referentiels charges une fois par import ; lignesVues detecte les doublons dans le fichier. */
    record Contexte(PerimetreDTO perimetre, Map<String, SousType> sousTypes, String listeSousTypes,
            Map<String, Client> clients, Map<String, FactureExistante> existantes, Map<String, Integer> lignesVues) {

        boolean dansPerimetre(Long agenceId) {
            return perimetre.isVisionGlobale() || perimetre.getAgenceIds().stream().anyMatch(a -> Objects.equals(a, agenceId));
        }
    }
}
