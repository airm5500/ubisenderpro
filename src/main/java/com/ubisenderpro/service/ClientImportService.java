package com.ubisenderpro.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubisenderpro.dto.ImportClientRequest;
import com.ubisenderpro.dto.ImportReport;
import com.ubisenderpro.entity.Client;
import com.ubisenderpro.entity.ClientContact;
import com.ubisenderpro.entity.ImportDetail;
import com.ubisenderpro.entity.ImportLog;
import com.ubisenderpro.entity.ImportMapping;
import com.ubisenderpro.entity.SegmentationClient;
import com.ubisenderpro.importer.FileParser;
import com.ubisenderpro.importer.ImportSupport;
import com.ubisenderpro.importer.PhoneNormalizer;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Import des comptes clients et contacts depuis Excel/CSV (sections 10 et 25 de la spec).
 */
@Stateless
public class ClientImportService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private ClientService clientService;
    @EJB
    private ContactService contactService;
    @EJB
    private SegmentationService segmentationService;
    @EJB
    private ImportMappingService importMappingService;

    public ImportReport importer(ImportClientRequest req, Long utilisateurId) {
        long debut = System.currentTimeMillis();
        ImportReport rapport = new ImportReport();
        rapport.setTypeImport("CLIENTS");

        String prefixePays = parametre("whatsapp.prefixe_pays", "225");

        // Mapping sauvegardé éventuel : prioritaire si fourni.
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
        log.setTypeImport("CLIENTS");
        log.setUtilisateurId(utilisateurId);
        log.setModeImport(req.isSimulation() ? "SIMULATION" : req.getMode());
        em.persist(log);

        List<Map<String, String>> lignes;
        try {
            byte[] contenu = Base64.getDecoder().decode(req.getFichierBase64());
            char sep = req.getSeparateur() != null && !req.getSeparateur().isEmpty()
                    ? req.getSeparateur().charAt(0) : ';';
            lignes = FileParser.parse(contenu, req.getNomFichier(), sep);
        } catch (Exception e) {
            log.setStatut("ECHEC");
            log.setFichierErreurs("Lecture du fichier impossible : " + e.getMessage());
            em.merge(log);
            rapport.ajouterErreur(0, "Lecture du fichier impossible : " + e.getMessage());
            return rapport;
        }

        rapport.setLignesLues(lignes.size());
        int numLigne = 1; // ligne 1 = en-tête

        for (Map<String, String> ligne : lignes) {
            numLigne++;
            try {
                traiterLigne(ligne, req, prefixePays, rapport, numLigne, log.getId());
            } catch (Exception e) {
                rapport.setLignesRejetees(rapport.getLignesRejetees() + 1);
                rapport.ajouterErreur(numLigne, e.getMessage());
                ImportSupport.enregistrerDetail(em, log.getId(), numLigne, "REJETE", e.getMessage(), ligne);
            }
        }

        log.setNbLignes(rapport.getLignesLues());
        log.setNbCrees(rapport.getComptesCrees() + rapport.getContactsCrees());
        log.setNbMisAJour(rapport.getComptesMisAJour() + rapport.getContactsMisAJour());
        log.setNbIgnores(rapport.getLignesIgnorees());
        log.setNbRejetes(rapport.getLignesRejetees());
        log.setDureeMs(System.currentTimeMillis() - debut);
        log.setStatut(req.isSimulation() ? "SIMULATION" : "TERMINE");
        if (!rapport.getErreurs().isEmpty()) {
            log.setFichierErreurs(String.join("\n", rapport.getErreurs()));
        }
        em.merge(log);
        rapport.setImportId(log.getId());
        return rapport;
    }

    private void traiterLigne(Map<String, String> ligne, ImportClientRequest req,
                              String prefixePays, ImportReport rapport, int numLigne, Long importId) {
        String numeroClient = val(ligne, req, "numero_client");
        String nomCompte = val(ligne, req, "nom_compte");

        if (numeroClient == null || numeroClient.isEmpty()) {
            rapport.setLignesRejetees(rapport.getLignesRejetees() + 1);
            rapport.ajouterErreur(numLigne, "Numéro client manquant");
            ImportSupport.enregistrerDetail(em, importId, numLigne, "REJETE", "Numéro client manquant", ligne);
            return;
        }
        if (nomCompte == null || nomCompte.isEmpty()) {
            rapport.setLignesRejetees(rapport.getLignesRejetees() + 1);
            rapport.ajouterErreur(numLigne, "Nom du compte manquant");
            ImportSupport.enregistrerDetail(em, importId, numLigne, "REJETE", "Nom du compte manquant", ligne);
            return;
        }

        // --- Compte client : créer ou mettre à jour ---
        Optional<Client> existant = clientService.parNumero(numeroClient);
        // Mode IGNORER : on n'écrase pas un compte existant.
        if (existant.isPresent() && "IGNORER".equalsIgnoreCase(req.getMode())) {
            rapport.setLignesIgnorees(rapport.getLignesIgnorees() + 1);
            ImportSupport.enregistrerDetail(em, importId, numLigne, "IGNORE", "Doublon ignoré (mode IGNORER)", ligne);
            return;
        }
        Client client = existant.orElseGet(Client::new);
        boolean creation = !existant.isPresent();

        client.setNumeroClient(numeroClient);
        client.setNomCompte(nomCompte);
        appliquerSiPresent(val(ligne, req, "agence"), client::setAgence);
        appliquerSiPresent(val(ligne, req, "region"), client::setRegion);
        appliquerSiPresent(val(ligne, req, "email_principal"), client::setEmailPrincipal);
        appliquerSiPresent(val(ligne, req, "adresse"), client::setAdresse);
        appliquerSiPresent(val(ligne, req, "ville"), client::setVille);
        appliquerSiPresent(val(ligne, req, "commune"), client::setCommune);
        appliquerSiPresent(val(ligne, req, "pays"), client::setPays);
        appliquerSiPresent(val(ligne, req, "notes"), client::setNotes);
        String statut = val(ligne, req, "statut");
        if (statut != null && !statut.isEmpty()) client.setStatut(statut.toUpperCase());

        String segLibelle = val(ligne, req, "segmentation");
        if (segLibelle != null && !segLibelle.isEmpty()) {
            Optional<SegmentationClient> seg = segmentationService.resoudre(segLibelle, req.isCreerSegmentation());
            seg.ifPresent(s -> client.setSegmentationId(s.getId()));
        }

        if (!req.isSimulation()) {
            if (creation) clientService.creer(client);
            else clientService.modifier(client);
        }

        if (creation) rapport.setComptesCrees(rapport.getComptesCrees() + 1);
        else rapport.setComptesMisAJour(rapport.getComptesMisAJour() + 1);

        // --- Contact principal ---
        String nomContact = val(ligne, req, "contact_principal");
        if (nomContact == null || nomContact.isEmpty()) {
            return; // un client sans contact est accepté
        }

        String telephone = val(ligne, req, "telephone_principal");
        String whatsappBrut = val(ligne, req, "numero_whatsapp");
        // Correction éventuelle saisie à l'écran d'aperçu (clé = n° de ligne).
        String correction = req.getCorrectionsNumero() == null ? null
                : req.getCorrectionsNumero().get(String.valueOf(numLigne));
        if (correction != null && !correction.trim().isEmpty()) {
            whatsappBrut = correction.trim();
        }
        String whatsappNorm = null;
        if (whatsappBrut != null && !whatsappBrut.isEmpty()) {
            PhoneNormalizer.Result r = PhoneNormalizer.normaliser(whatsappBrut, prefixePays);
            if (r.valide) {
                whatsappNorm = r.valeurNormalisee;
            } else {
                rapport.ajouterErreur(numLigne, "WhatsApp : " + r.message);
                rapport.ajouterNumeroInvalide(numLigne, nomContact, whatsappBrut, r.message);
            }
        }

        Long clientId = client.getId();
        Optional<ClientContact> doublon = clientId == null ? Optional.empty()
                : contactService.trouverDoublon(clientId, whatsappNorm, telephone, nomContact);
        ClientContact contact = doublon.orElseGet(ClientContact::new);
        boolean creationContact = !doublon.isPresent();

        contact.setClientId(clientId);
        contact.setNomComplet(nomContact);
        contact.setContactPrincipal(true);
        appliquerSiPresent(val(ligne, req, "fonction"), contact::setFonction);
        appliquerSiPresent(telephone, contact::setTelephonePrincipal);
        appliquerSiPresent(val(ligne, req, "telephone_2"), contact::setTelephone2);
        appliquerSiPresent(val(ligne, req, "email_principal"), contact::setEmail);
        if (whatsappNorm != null) contact.setNumeroWhatsapp(whatsappNorm);
        String consent = val(ligne, req, "consentement_whatsapp");
        if (consent != null) {
            contact.setConsentementWhatsapp(consent.equalsIgnoreCase("oui")
                    || consent.equalsIgnoreCase("true") || consent.equals("1"));
        }

        if (!req.isSimulation() && clientId != null) {
            if (creationContact) contactService.creer(contact);
            else contactService.modifier(contact);
        }

        if (creationContact) rapport.setContactsCrees(rapport.getContactsCrees() + 1);
        else rapport.setContactsMisAJour(rapport.getContactsMisAJour() + 1);

        if (contact.getNumeroWhatsapp() != null && !contact.getNumeroWhatsapp().isEmpty()) {
            rapport.setContactsWhatsapp(rapport.getContactsWhatsapp() + 1);
        } else {
            rapport.setContactsSansTelephone(rapport.getContactsSansTelephone() + 1);
        }
    }

    private void appliquerSiPresent(String valeur, java.util.function.Consumer<String> setter) {
        if (valeur != null && !valeur.isEmpty()) {
            setter.accept(valeur);
        }
    }

    private String val(Map<String, String> ligne, ImportClientRequest req, String champLogique) {
        String colonne = req.getMapping().get(champLogique);
        if (colonne == null) {
            // À défaut de mapping, on tente le nom logique comme nom de colonne.
            colonne = champLogique;
        }
        String v = ligne.get(colonne);
        return v == null ? null : v.trim();
    }

    private String parametre(String cle, String defaut) {
        List<String> r = em.createNativeQuery(
                "SELECT valeur FROM usp_parametre WHERE cle = ?1")
                .setParameter(1, cle)
                .getResultList();
        return r.isEmpty() || r.get(0) == null ? defaut : r.get(0);
    }
}
