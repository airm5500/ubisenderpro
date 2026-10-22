package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.InformationService;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.InformationDTO;
import ci.ubipharm.crm.rest.service.dto.InformationDemandeDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

/**
 * Informations clients et alertes operationnelles, reprises d'UbiSenderPro : une information porte le contexte
 * (tournee, heure estimee, pharmacien de garde...) que ses variables citent ; la campagne qui l'annonce se programme
 * et se valide comme les autres campagnes du Marketing.
 */
@Service
@RequiredArgsConstructor
public class InformationServiceImpl implements InformationService {

    static final Set<String> TYPES = Set.of("RETARD_LIVRAISON", "MODIFICATION_TOURNEE", "ANNULATION_TOURNEE", "REPRISE_LIVRAISON",
            "INFORMATION_GARDE", "INFORMATION_JOUR_FERIE", "MODIFICATION_HORAIRES", "FERMETURE_AGENCE", "INFORMATION_GENERALE",
            "ALERTE_URGENTE", "RETRAIT_PRODUIT");
    private static final Set<String> LIVRAISON = Set.of("RETARD_LIVRAISON", "MODIFICATION_TOURNEE", "REPRISE_LIVRAISON");
    private static final Set<String> GARDE = Set.of("INFORMATION_GARDE", "INFORMATION_JOUR_FERIE");
    /** Types dont le modele cite le texte libre {information_message}. */
    private static final Set<String> TEXTE_LIBRE = Set.of("MODIFICATION_HORAIRES", "FERMETURE_AGENCE", "INFORMATION_GENERALE",
            "ALERTE_URGENTE", "RETRAIT_PRODUIT");
    private static final Set<String> PRIORITES = Set.of("NORMALE", "IMPORTANTE", "URGENTE", "CRITIQUE");
    private static final DateTimeFormatter DATE_LISIBLE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String SELECT = """
            select i.id as id, i.titre as titre, i.type_information as typeInformation, i.priorite as priorite, i.message as message,
                   i.agence_id as agenceId, a.libelle as agence, i.tournee as tournee, i.signataire as signataire, i.fin_validite as finValidite,
                   i.date_livraison as dateLivraison, i.creneau as creneau, i.heure_initiale as heureInitiale, i.nouvelle_heure as nouvelleHeure,
                   i.cause_communicable as causeCommunicable, i.cause_interne as causeInterne, i.date_resolution as dateResolution,
                   i.jour_ferie as jourFerie, i.date_garde as dateGarde, i.heure_limite_commande as heureLimiteCommande,
                   i.consignes_livraison as consignesLivraison, i.pharmacien_garde as pharmacienGarde, i.telephone_pharmacien as telephonePharmacien,
                   i.statut as statut, uc.nom_complet as creePar, coalesce(um.nom_complet, uc.nom_complet) as modifiePar,
                   coalesce(i.updated_at, i.created_at) as modifieLe,
                   (select count(*) from crm_campagne c where c.information_id = i.id) as nbCampagnes,
                   (select c.nom from crm_campagne c where c.information_id = i.id order by c.id desc limit 1) as derniereCampagne,
                   (select c.statut from crm_campagne c where c.information_id = i.id order by c.id desc limit 1) as derniereCampagneStatut
            from crm_information i
            left join crm_agence a on a.id = i.agence_id
            join crm_utilisateur uc on uc.id = i.cree_par
            left join crm_utilisateur um on um.id = i.modifie_par
            """;

    /** Colonnes saisies, dans l'ordre de l'insertion. */
    private static final String COLONNES = """
            titre, type_information, priorite, message, agence_id, tournee, signataire, fin_validite, date_livraison, creneau,
            heure_initiale, nouvelle_heure, cause_communicable, cause_interne, date_resolution, jour_ferie, date_garde,
            heure_limite_commande, consignes_livraison, pharmacien_garde, telephone_pharmacien""";

    private final EntityManager em;
    private final JournalActionService journalActionService;
    private final UtilisateurCourantService utilisateurCourantService;

    @Override
    @Transactional(readOnly = true)
    public List<InformationDTO> lister(boolean archives) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where i.statut = :statut order by coalesce(i.updated_at, i.created_at) desc, i.id desc",
                Tuple.class).setParameter("statut", archives ? "ARCHIVE" : "ACTIF").getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InformationDTO consulter(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where i.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Information introuvable.");
        }
        return mapTupleToDto(lignes.getFirst());
    }

    @Override
    @Transactional
    public InformationDTO creer(InformationDemandeDTO demande) {
        verifier(demande, true);
        Query insertion = em.createNativeQuery("insert into crm_information (" + COLONNES + ", cree_par, created_at) values ("
                + ":titre, :type, :priorite, :message, :agenceId, :tournee, :signataire, :finValidite, :dateLivraison, :creneau, "
                + ":heureInitiale, :nouvelleHeure, :causeCommunicable, :causeInterne, :dateResolution, :jourFerie, :dateGarde, "
                + ":heureLimite, :consignes, :pharmacien, :telephone, :par, utc_timestamp())");
        parametrer(insertion, demande).executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        InformationDTO cree = consulter(id);
        journalActionService.enregistrer("INFORMATION_CREATION", "crm_information", id, resume(cree));
        return cree;
    }

    @Override
    @Transactional
    public InformationDTO modifier(Long id, InformationDemandeDTO demande) {
        InformationDTO actuel = consulter(id);
        if ("ARCHIVE".equals(actuel.getStatut())) {
            throw new ErreurMetierException("Réactivez d'abord cette information pour la modifier.");
        }
        verifier(demande, false);
        Query maj = em.createNativeQuery("""
                update crm_information set titre = :titre, type_information = :type, priorite = :priorite, message = :message,
                       agence_id = :agenceId, tournee = :tournee, signataire = :signataire, fin_validite = :finValidite,
                       date_livraison = :dateLivraison, creneau = :creneau, heure_initiale = :heureInitiale, nouvelle_heure = :nouvelleHeure,
                       cause_communicable = :causeCommunicable, cause_interne = :causeInterne, date_resolution = :dateResolution,
                       jour_ferie = :jourFerie, date_garde = :dateGarde, heure_limite_commande = :heureLimite,
                       consignes_livraison = :consignes, pharmacien_garde = :pharmacien, telephone_pharmacien = :telephone,
                       modifie_par = :par, updated_at = utc_timestamp()
                where id = :id
                """);
        parametrer(maj, demande).setParameter("id", id).executeUpdate();
        InformationDTO modifie = consulter(id);
        journalActionService.enregistrer("INFORMATION_MODIFICATION", "crm_information", id, resume(actuel) + " -> " + resume(modifie));
        return modifie;
    }

    @Override
    @Transactional
    public InformationDTO dupliquer(Long id) {
        InformationDTO source = consulter(id);
        // Une copie repart d'une validite ouverte : la situation annoncee est nouvelle
        em.createNativeQuery("insert into crm_information (" + COLONNES + ", cree_par, created_at) select "
                + "left(concat(titre, ' (copie)'), 200), type_information, priorite, message, agence_id, tournee, signataire, "
                + "case when fin_validite < utc_date() then null else fin_validite end, date_livraison, creneau, heure_initiale, nouvelle_heure, "
                + "cause_communicable, cause_interne, date_resolution, jour_ferie, date_garde, heure_limite_commande, consignes_livraison, "
                + "pharmacien_garde, telephone_pharmacien, :par, utc_timestamp() from crm_information where id = :id")
                .setParameter("par", utilisateurCourantService.utilisateur().getId()).setParameter("id", id).executeUpdate();
        Long copie = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        journalActionService.enregistrer("INFORMATION_DUPLICATION", "crm_information", copie, "Copie de " + source.getTitre());
        return consulter(copie);
    }

    @Override
    @Transactional
    public InformationDTO archiver(Long id) {
        InformationDTO actuel = consulter(id);
        @SuppressWarnings("unchecked")
        List<String> campagnes = em.createNativeQuery("""
                select nom from crm_campagne where information_id = :id and statut in ('BROUILLON', 'A_VALIDER', 'VALIDEE', 'EN_COURS') order by nom
                """).setParameter("id", id).getResultList();
        if (!campagnes.isEmpty()) {
            throw new ErreurMetierException("L'information est annoncée par des campagnes en préparation ou programmées : "
                    + String.join(", ", campagnes) + ". Annulez-les d'abord.");
        }
        changerStatut(id, "ARCHIVE");
        journalActionService.enregistrer("INFORMATION_ARCHIVAGE", "crm_information", id, actuel.getTitre());
        return consulter(id);
    }

    @Override
    @Transactional
    public InformationDTO reactiver(Long id) {
        InformationDTO actuel = consulter(id);
        changerStatut(id, "ACTIF");
        journalActionService.enregistrer("INFORMATION_REACTIVATION", "crm_information", id, actuel.getTitre());
        return consulter(id);
    }

    @Override
    @Transactional(readOnly = true)
    public void verifierCitable(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where i.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty() || !"ACTIF".equals(lignes.getFirst().get("statut", String.class))) {
            throw new ErreurMetierException("Choisissez une information active.");
        }
        InformationDTO information = mapTupleToDto(lignes.getFirst());
        if (information.isExpiree()) {
            throw new ErreurMetierException("L'information « " + information.getTitre() + " » n'est plus valable depuis le "
                    + information.getFinValidite().format(DATE_LISIBLE) + ".");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> valeursCampagne(Long id) {
        InformationDTO i = consulter(id);
        Map<String, String> v = new HashMap<>();
        v.put("calcul.information_titre", i.getTitre());
        v.put("calcul.information_message", texte(i.getMessage()));
        v.put("calcul.tournee", texte(i.getTournee()));
        v.put("calcul.agence_ou_tournee", !texte(i.getTournee()).isEmpty() ? i.getTournee() : texte(i.getAgence()));
        v.put("calcul.date_livraison", date(i.getDateLivraison()));
        v.put("calcul.creneau", texte(i.getCreneau()));
        v.put("calcul.nouvelle_estimation_livraison", texte(i.getNouvelleHeure()).isEmpty() ? ""
                : "Nouvelle estimation de livraison : " + i.getNouvelleHeure().strip());
        v.put("calcul.cause_retard", texte(i.getCauseCommunicable()));
        v.put("calcul.date_resolution", date(i.getDateResolution()));
        v.put("calcul.jour_ferie", texte(i.getJourFerie()));
        v.put("calcul.date_garde", date(i.getDateGarde()));
        v.put("calcul.heure_limite_commande", texte(i.getHeureLimiteCommande()));
        v.put("calcul.consignes_livraison", texte(i.getConsignesLivraison()));
        v.put("calcul.pharmacien_garde", texte(i.getPharmacienGarde()));
        v.put("calcul.telephone_pharmacien", texte(i.getTelephonePharmacien()));
        v.put("calcul.direction_signataire", !texte(i.getSignataire()).isEmpty() ? i.getSignataire().strip()
                : "Direction " + (texte(i.getAgence()).isEmpty() ? "commerciale" : i.getAgence()));
        return v;
    }

    private void verifier(InformationDemandeDTO d, boolean creation) {
        String type = d.getTypeInformation();
        if (!TYPES.contains(type)) {
            throw new ErreurMetierException("Type d'information inconnu : " + type + ".");
        }
        if (d.getPriorite() != null && !d.getPriorite().isBlank() && !PRIORITES.contains(d.getPriorite())) {
            throw new ErreurMetierException("Priorité inconnue : " + d.getPriorite() + ".");
        }
        if (d.getAgenceId() != null && ((Number) em.createNativeQuery("select count(*) from crm_agence where id = :id and statut = 'ACTIF'")
                .setParameter("id", d.getAgenceId()).getSingleResult()).longValue() == 0) {
            throw new ErreurMetierException("Choisissez une agence active.");
        }
        if (TEXTE_LIBRE.contains(type) && texte(d.getMessage()).isEmpty()) {
            throw new ErreurMetierException("Saisissez le message : le modèle de ce type d'information le cite.");
        }
        if (LIVRAISON.contains(type) && d.getAgenceId() == null && texte(d.getTournee()).isEmpty()) {
            throw new ErreurMetierException("Précisez l'agence ou la tournée concernée par la livraison.");
        }
        if ("ANNULATION_TOURNEE".equals(type) && texte(d.getTournee()).isEmpty()) {
            throw new ErreurMetierException("Précisez la tournée annulée.");
        }
        if (GARDE.contains(type) && (texte(d.getJourFerie()).isEmpty() || texte(d.getHeureLimiteCommande()).isEmpty())) {
            throw new ErreurMetierException("Précisez le jour férié et l'heure limite de commande.");
        }
        if (d.getDateLivraison() != null && d.getDateResolution() != null && d.getDateResolution().isBefore(d.getDateLivraison())) {
            throw new ErreurMetierException("La résolution estimée précède la date de livraison.");
        }
        if (creation && d.getFinValidite() != null && d.getFinValidite().isBefore(aujourdhui())) {
            throw new ErreurMetierException("La fin de validité est déjà passée.");
        }
    }

    private Query parametrer(Query q, InformationDemandeDTO d) {
        return q.setParameter("titre", d.getTitre().strip()).setParameter("type", d.getTypeInformation())
                .setParameter("priorite", d.getPriorite() == null || d.getPriorite().isBlank() ? "NORMALE" : d.getPriorite())
                .setParameter("message", nul(d.getMessage())).setParameter("agenceId", d.getAgenceId())
                .setParameter("tournee", nul(d.getTournee())).setParameter("signataire", nul(d.getSignataire()))
                .setParameter("finValidite", d.getFinValidite()).setParameter("dateLivraison", d.getDateLivraison())
                .setParameter("creneau", nul(d.getCreneau())).setParameter("heureInitiale", nul(d.getHeureInitiale()))
                .setParameter("nouvelleHeure", nul(d.getNouvelleHeure())).setParameter("causeCommunicable", nul(d.getCauseCommunicable()))
                .setParameter("causeInterne", nul(d.getCauseInterne())).setParameter("dateResolution", d.getDateResolution())
                .setParameter("jourFerie", nul(d.getJourFerie())).setParameter("dateGarde", d.getDateGarde())
                .setParameter("heureLimite", nul(d.getHeureLimiteCommande())).setParameter("consignes", nul(d.getConsignesLivraison()))
                .setParameter("pharmacien", nul(d.getPharmacienGarde())).setParameter("telephone", nul(d.getTelephonePharmacien()))
                .setParameter("par", utilisateurCourantService.utilisateur().getId());
    }

    private void changerStatut(Long id, String statut) {
        em.createNativeQuery("update crm_information set statut = :statut, modifie_par = :par, updated_at = utc_timestamp() where id = :id")
                .setParameter("statut", statut).setParameter("par", utilisateurCourantService.utilisateur().getId()).setParameter("id", id)
                .executeUpdate();
    }

    private static String texte(String valeur) {
        return valeur == null ? "" : valeur.strip();
    }

    private static String nul(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.strip();
    }

    private static String date(LocalDate date) {
        return date == null ? "" : date.format(DATE_LISIBLE);
    }

    private static String resume(InformationDTO i) {
        return i.getTitre() + " (" + i.getTypeInformation() + (i.getAgence() != null ? ", " + i.getAgence() : "")
                + (i.getTournee() != null ? ", " + i.getTournee() : "") + ")";
    }

    private static LocalDate aujourdhui() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    InformationDTO mapTupleToDto(Tuple t) {
        LocalDate finValidite = t.get("finValidite", LocalDate.class);
        return InformationDTO.builder()
                .id(t.get("id", Long.class))
                .titre(t.get("titre", String.class))
                .typeInformation(t.get("typeInformation", String.class))
                .priorite(t.get("priorite", String.class))
                .message(t.get("message", String.class))
                .agenceId(t.get("agenceId", Long.class))
                .agence(t.get("agence", String.class))
                .tournee(t.get("tournee", String.class))
                .signataire(t.get("signataire", String.class))
                .finValidite(finValidite)
                .dateLivraison(t.get("dateLivraison", LocalDate.class))
                .creneau(t.get("creneau", String.class))
                .heureInitiale(t.get("heureInitiale", String.class))
                .nouvelleHeure(t.get("nouvelleHeure", String.class))
                .causeCommunicable(t.get("causeCommunicable", String.class))
                .causeInterne(t.get("causeInterne", String.class))
                .dateResolution(t.get("dateResolution", LocalDate.class))
                .jourFerie(t.get("jourFerie", String.class))
                .dateGarde(t.get("dateGarde", LocalDate.class))
                .heureLimiteCommande(t.get("heureLimiteCommande", String.class))
                .consignesLivraison(t.get("consignesLivraison", String.class))
                .pharmacienGarde(t.get("pharmacienGarde", String.class))
                .telephonePharmacien(t.get("telephonePharmacien", String.class))
                .statut(t.get("statut", String.class))
                .expiree(finValidite != null && finValidite.isBefore(aujourdhui()))
                .nbCampagnes(((Number) Objects.requireNonNullElse(t.get("nbCampagnes"), 0)).intValue())
                .derniereCampagne(t.get("derniereCampagne", String.class))
                .derniereCampagneStatut(t.get("derniereCampagneStatut", String.class))
                .creePar(t.get("creePar", String.class))
                .modifiePar(t.get("modifiePar", String.class))
                .modifieLe(t.get("modifieLe", LocalDateTime.class))
                .build();
    }
}
