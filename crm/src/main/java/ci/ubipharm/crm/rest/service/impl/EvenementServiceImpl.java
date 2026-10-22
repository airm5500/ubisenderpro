package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.EvenementService;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.EvenementDTO;
import ci.ubipharm.crm.rest.service.dto.EvenementDemandeDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

/**
 * Calendrier des evenements annonces aux clients : un evenement annuel revient chaque annee a la meme date,
 * les autres (fetes a date variable, fermetures) se saisissent pour l'annee.
 */
@Service
@RequiredArgsConstructor
public class EvenementServiceImpl implements EvenementService {

    static final String JOURS_AVANT = "marketing.evenement_jours_avant";
    static final String JOURS_AVANT_FERMETURE = "marketing.fermeture_jours_avant";
    static final String HEURE = "marketing.evenement_heure";
    private static final Set<String> NATURES = Set.of("FETE", "JOUR_FERIE", "FERMETURE", "AUTRE");
    private static final DateTimeFormatter DATE_LISIBLE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String SELECT = """
            select e.id as id, e.nom as nom, e.nature as nature, e.date_debut as dateDebut, e.date_fin as dateFin, e.annuel as annuel,
                   e.statut as statut, coalesce(um.nom_complet, uc.nom_complet) as modifiePar, coalesce(e.updated_at, e.created_at) as modifieLe
            from crm_evenement e
            left join crm_utilisateur uc on uc.id = e.cree_par
            left join crm_utilisateur um on um.id = e.modifie_par
            """;

    private final EntityManager em;
    private final ParametreService parametreService;
    private final JournalActionService journalActionService;
    private final UtilisateurCourantService utilisateurCourantService;

    /** Occurrence d'un evenement : premier et dernier jour. */
    record Occurrence(LocalDate debut, LocalDate fin) {
    }

    /**
     * Premiere occurrence qui n'est pas finie a la date de reference ; pour un evenement ponctuel, son unique
     * occurrence, meme passee.
     */
    static Occurrence occurrence(LocalDate debut, LocalDate fin, boolean annuel, LocalDate reference) {
        LocalDate dernier = fin == null ? debut : fin;
        if (!annuel) {
            return new Occurrence(debut, dernier);
        }
        long duree = ChronoUnit.DAYS.between(debut, dernier);
        for (int annees = reference.getYear() - debut.getYear() - 1; ; annees++) {
            LocalDate d = debut.plusYears(annees);
            if (!d.plusDays(duree).isBefore(reference)) {
                return new Occurrence(d, d.plusDays(duree));
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvenementDTO> lister(boolean archives) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where e.statut = :statut", Tuple.class)
                .setParameter("statut", archives ? "ARCHIVE" : "ACTIF").getResultList();
        // Les evenements a venir d'abord, du plus proche au plus lointain ; les passes ensuite, du plus recent
        return lignes.stream().map(this::mapTupleToDto)
                .sorted(Comparator.comparing((EvenementDTO e) -> e.getProchaineDate() == null)
                        .thenComparing(e -> e.getProchaineDate() != null ? e.getProchaineDate().toEpochDay() : -e.getDateDebut().toEpochDay())
                        .thenComparing(EvenementDTO::getNom))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EvenementDTO consulter(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where e.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Événement introuvable.");
        }
        return mapTupleToDto(lignes.getFirst());
    }

    @Override
    @Transactional
    public EvenementDTO creer(EvenementDemandeDTO demande) {
        verifier(demande, null);
        em.createNativeQuery("""
                insert into crm_evenement (nom, nature, date_debut, date_fin, annuel, cree_par, created_at)
                values (:nom, :nature, :debut, :fin, :annuel, :par, utc_timestamp())
                """)
                .setParameter("nom", demande.getNom().trim()).setParameter("nature", demande.getNature())
                .setParameter("debut", demande.getDateDebut()).setParameter("fin", fin(demande)).setParameter("annuel", demande.isAnnuel())
                .setParameter("par", utilisateurCourantService.utilisateur().getId())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        EvenementDTO cree = consulter(id);
        journalActionService.enregistrer("EVENEMENT_CREATION", "crm_evenement", id, resume(cree));
        return cree;
    }

    @Override
    @Transactional
    public EvenementDTO modifier(Long id, EvenementDemandeDTO demande) {
        EvenementDTO actuel = consulter(id);
        if ("ARCHIVE".equals(actuel.getStatut())) {
            throw new ErreurMetierException("Réactivez d'abord cet événement pour le modifier.");
        }
        verifier(demande, id);
        em.createNativeQuery("""
                update crm_evenement set nom = :nom, nature = :nature, date_debut = :debut, date_fin = :fin, annuel = :annuel,
                       modifie_par = :par, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("nom", demande.getNom().trim()).setParameter("nature", demande.getNature())
                .setParameter("debut", demande.getDateDebut()).setParameter("fin", fin(demande)).setParameter("annuel", demande.isAnnuel())
                .setParameter("par", utilisateurCourantService.utilisateur().getId()).setParameter("id", id)
                .executeUpdate();
        EvenementDTO modifie = consulter(id);
        journalActionService.enregistrer("EVENEMENT_MODIFICATION", "crm_evenement", id, resume(actuel) + " -> " + resume(modifie));
        return modifie;
    }

    @Override
    @Transactional
    public EvenementDTO archiver(Long id) {
        EvenementDTO actuel = consulter(id);
        @SuppressWarnings("unchecked")
        List<String> campagnes = em.createNativeQuery("""
                select nom from crm_campagne where evenement_id = :id and statut in ('BROUILLON', 'A_VALIDER', 'VALIDEE', 'EN_COURS') order by nom
                """).setParameter("id", id).getResultList();
        if (!campagnes.isEmpty()) {
            throw new ErreurMetierException("L'événement est annoncé par des campagnes en préparation ou programmées : "
                    + String.join(", ", campagnes) + ". Annulez-les d'abord.");
        }
        changerStatut(id, "ARCHIVE");
        journalActionService.enregistrer("EVENEMENT_ARCHIVAGE", "crm_evenement", id, actuel.getNom());
        return consulter(id);
    }

    @Override
    @Transactional
    public EvenementDTO reactiver(Long id) {
        EvenementDTO actuel = consulter(id);
        if (doublon(actuel.getNom(), actuel.getDateDebut(), id)) {
            throw new ErreurMetierException("Un événement actif « " + actuel.getNom() + " » existe déjà à cette date.");
        }
        changerStatut(id, "ACTIF");
        journalActionService.enregistrer("EVENEMENT_REACTIVATION", "crm_evenement", id, actuel.getNom());
        return consulter(id);
    }

    @Override
    @Transactional(readOnly = true)
    public void verifierCitable(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where e.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty() || !"ACTIF".equals(lignes.getFirst().get("statut", String.class))) {
            throw new ErreurMetierException("Choisissez un événement actif du calendrier.");
        }
        EvenementDTO evenement = mapTupleToDto(lignes.getFirst());
        if (evenement.getProchaineDate() == null) {
            throw new ErreurMetierException("L'événement « " + evenement.getNom() + " » est passé.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> valeursCampagne(Long id, LocalDate reference) {
        EvenementDTO evenement = consulter(id);
        Occurrence o = occurrence(evenement.getDateDebut(), evenement.getDateFin(), evenement.isAnnuel(), reference);
        Map<String, String> valeurs = new HashMap<>();
        valeurs.put("calcul.evenement_nom", evenement.getNom());
        valeurs.put("calcul.evenement_date", o.debut().format(DATE_LISIBLE));
        valeurs.put("calcul.evenement_fin", o.fin().format(DATE_LISIBLE));
        return valeurs;
    }

    private void verifier(EvenementDemandeDTO demande, Long id) {
        if (!NATURES.contains(demande.getNature())) {
            throw new ErreurMetierException("Nature d'événement inconnue : " + demande.getNature() + ".");
        }
        LocalDate fin = fin(demande);
        if (fin != null && fin.isBefore(demande.getDateDebut())) {
            throw new ErreurMetierException("Le dernier jour précède le premier.");
        }
        if (fin != null && demande.isAnnuel() && ChronoUnit.DAYS.between(demande.getDateDebut(), fin) > 60) {
            throw new ErreurMetierException("Un événement qui revient chaque année dure au plus 60 jours.");
        }
        if (id == null && !demande.isAnnuel() && (fin != null ? fin : demande.getDateDebut()).isBefore(aujourdhui())) {
            throw new ErreurMetierException("Cet événement est déjà passé.");
        }
        if (doublon(demande.getNom().trim(), demande.getDateDebut(), id)) {
            throw new ErreurMetierException("Un événement « " + demande.getNom().trim() + " » existe déjà à cette date.");
        }
    }

    private boolean doublon(String nom, LocalDate debut, Long id) {
        return ((Number) em.createNativeQuery("""
                select count(*) from crm_evenement where statut = 'ACTIF' and id <> :id and nom = :nom and date_debut = :debut
                """).setParameter("id", id == null ? 0L : id).setParameter("nom", nom).setParameter("debut", debut)
                .getSingleResult()).longValue() > 0;
    }

    private void changerStatut(Long id, String statut) {
        em.createNativeQuery("update crm_evenement set statut = :statut, modifie_par = :par, updated_at = utc_timestamp() where id = :id")
                .setParameter("statut", statut).setParameter("par", utilisateurCourantService.utilisateur().getId()).setParameter("id", id)
                .executeUpdate();
    }

    /** Un jour unique se note sans date de fin. */
    private static LocalDate fin(EvenementDemandeDTO demande) {
        return demande.getDateFin() == null || demande.getDateFin().equals(demande.getDateDebut()) ? null : demande.getDateFin();
    }

    private static String resume(EvenementDTO e) {
        return e.getNom() + " (" + e.getDateDebut().format(DATE_LISIBLE)
                + (e.getDateFin() != null ? " au " + e.getDateFin().format(DATE_LISIBLE) : "") + (e.isAnnuel() ? ", chaque année" : "") + ")";
    }

    private static LocalDate aujourdhui() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    EvenementDTO mapTupleToDto(Tuple tuple) {
        LocalDate debut = tuple.get("dateDebut", LocalDate.class);
        LocalDate fin = tuple.get("dateFin", LocalDate.class);
        boolean annuel = Boolean.TRUE.equals(tuple.get("annuel", Boolean.class));
        Occurrence o = occurrence(debut, fin, annuel, aujourdhui());
        boolean aVenir = !o.fin().isBefore(aujourdhui());
        LocalDateTime envoi = null;
        if (aVenir) {
            // Une fermeture s'annonce a l'avance pour que les clients commandent ; une fete se souhaite le jour meme
            int joursAvant = "FERMETURE".equals(tuple.get("nature", String.class))
                    ? parametreService.entier(JOURS_AVANT_FERMETURE, 3) : parametreService.entier(JOURS_AVANT, 0);
            envoi = o.debut().minusDays(joursAvant).atTime(parametreService.entier(HEURE, 8), 0);
            // Evenement deja commence ou envoi propose deja passe : prochaine heure pleine
            LocalDateTime maintenant = LocalDateTime.now(ZoneOffset.UTC);
            if (envoi.isBefore(maintenant)) {
                envoi = maintenant.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            }
        }
        return EvenementDTO.builder()
                .id(tuple.get("id", Long.class))
                .nom(tuple.get("nom", String.class))
                .nature(tuple.get("nature", String.class))
                .dateDebut(debut)
                .dateFin(fin)
                .annuel(annuel)
                .statut(tuple.get("statut", String.class))
                .prochaineDate(aVenir ? o.debut() : null)
                .prochaineFin(aVenir ? o.fin() : null)
                .envoiPropose(envoi)
                .modifiePar(tuple.get("modifiePar", String.class))
                .modifieLe(tuple.get("modifieLe", LocalDateTime.class))
                .build();
    }
}
