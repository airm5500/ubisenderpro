package com.ubisenderpro.service;

import com.ubisenderpro.entity.Campagne;
import com.ubisenderpro.entity.EnvoiPropose;
import com.ubisenderpro.entity.Promotion;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Calendrier marketing : génère des <b>propositions d'envoi</b> à partir des
 * promotions (annonce mensuelle, lancement, rappels J-7 / J-3 / J-1) et gère
 * leur cycle de vie. Aucune proposition ne devient une campagne sans validation
 * humaine ({@link #valider}).
 */
@Stateless
public class EnvoiProposeService {

    /** Décalages (en jours avant la date de fin) des rappels générés. */
    private static final int[] RAPPELS_JOURS = { 3, 1 };
    private static final Locale FR = Locale.FRENCH;

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private PromotionService promotionService;
    @EJB
    private CampagneService campagneService;

    /* ====================== Lecture ====================== */

    public List<EnvoiPropose> lister(String statut) {
        if (statut == null || statut.isEmpty()) {
            return em.createQuery(
                    "SELECT e FROM EnvoiPropose e ORDER BY e.datePrevue, e.type", EnvoiPropose.class)
                    .getResultList();
        }
        return em.createQuery(
                "SELECT e FROM EnvoiPropose e WHERE e.statut = :s ORDER BY e.datePrevue, e.type", EnvoiPropose.class)
                .setParameter("s", statut).getResultList();
    }

    public Optional<EnvoiPropose> parId(Long id) { return Optional.ofNullable(em.find(EnvoiPropose.class, id)); }

    private Optional<EnvoiPropose> parCle(String cle) {
        List<EnvoiPropose> l = em.createQuery(
                "SELECT e FROM EnvoiPropose e WHERE e.cle = :c", EnvoiPropose.class)
                .setParameter("c", cle).setMaxResults(1).getResultList();
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    /* ====================== Génération ====================== */

    /**
     * Génère/actualise les propositions à venir (idempotent : une proposition
     * existante n'est jamais dupliquée, et une décision humaine n'est jamais écrasée).
     *
     * @return le nombre de propositions créées.
     */
    public int genererPropositions() {
        LocalDate today = LocalDate.now();
        int crees = 0;

        // 1) Annonce mensuelle : une proposition pour le mois en cours s'il y a
        //    au moins une promotion active sur ce mois.
        crees += genererAnnonceMensuelle(today);

        // 2) Par promotion non terminée et non annulée/archivée : lancement + rappels.
        for (Promotion p : promotionService.lister()) {
            String st = p.getStatut();
            if ("ANNULEE".equals(st) || "ARCHIVEE".equals(st)) { continue; }

            // Lancement : à la date de début, si elle n'est pas déjà passée.
            if (p.getDateDebut() != null) {
                LocalDate debut = p.getDateDebut().toLocalDate();
                if (!debut.isBefore(today)) {
                    crees += upsert("LANCEMENT:" + p.getId(), "LANCEMENT", p.getId(), debut,
                            "Lancement — " + p.getNom(), messageLancement(p));
                }
            }

            // Rappels avant la fin : J-7, J-3, J-1 (uniquement si encore à venir).
            if (p.getDateFin() != null) {
                LocalDate fin = p.getDateFin().toLocalDate();
                for (int j : RAPPELS_JOURS) {
                    LocalDate prevue = fin.minusDays(j);
                    if (prevue.isBefore(today)) { continue; }
                    crees += upsert("RAPPEL_J" + j + ":" + p.getId(), "RAPPEL_J" + j, p.getId(), prevue,
                            "Rappel fin J-" + j + " — " + p.getNom(), messageRappel(p, j, fin));
                }
            }
        }
        return crees;
    }

    private int genererAnnonceMensuelle(LocalDate today) {
        LocalDate premier = today.withDayOfMonth(1);
        LocalDate finMois = today.withDayOfMonth(today.lengthOfMonth());
        List<Promotion> duMois = new ArrayList<>();
        for (Promotion p : promotionService.lister()) {
            String st = p.getStatut();
            if ("ANNULEE".equals(st) || "ARCHIVEE".equals(st)) { continue; }
            LocalDate d = p.getDateDebut() != null ? p.getDateDebut().toLocalDate() : null;
            LocalDate f = p.getDateFin() != null ? p.getDateFin().toLocalDate() : null;
            // Promo qui chevauche le mois courant (dates ouvertes = on inclut).
            boolean apresFin = f != null && f.isBefore(premier);
            boolean avantDebut = d != null && d.isAfter(finMois);
            if (!apresFin && !avantDebut) { duMois.add(p); }
        }
        if (duMois.isEmpty()) { return 0; }

        String mois = premier.getMonth().getDisplayName(TextStyle.FULL, FR);
        String moisCap = mois.substring(0, 1).toUpperCase(FR) + mois.substring(1);
        String cle = "ANNONCE:" + premier.getYear() + "-" + String.format("%02d", premier.getMonthValue());
        // L'annonce part le 1er du mois ; si déjà passé, on la prévoit pour aujourd'hui.
        LocalDate prevue = premier.isBefore(today) ? today : premier;

        StringBuilder msg = new StringBuilder("Promotions du mois de " + moisCap + " "
                + premier.getYear() + " :\n");
        for (Promotion p : duMois) { msg.append("• ").append(p.getNom()).append('\n'); }
        return upsert(cle, "ANNONCE_MENSUELLE", null, prevue,
                "Annonce promotions — " + moisCap + " " + premier.getYear(), msg.toString().trim());
    }

    /**
     * Crée la proposition si sa clé n'existe pas ; si elle existe et est encore
     * PROPOSEE, met à jour date/titre/message (les promos peuvent bouger).
     * Les propositions VALIDEE/REJETEE/EXPIREE ne sont jamais modifiées.
     */
    private int upsert(String cle, String type, Long promotionId, LocalDate datePrevue,
                       String titre, String message) {
        Optional<EnvoiPropose> ex = parCle(cle);
        if (ex.isPresent()) {
            EnvoiPropose e = ex.get();
            if ("PROPOSEE".equals(e.getStatut())) {
                e.setDatePrevue(datePrevue);
                e.setTitre(titre);
                e.setMessage(message);
                em.merge(e);
            }
            return 0;
        }
        EnvoiPropose e = new EnvoiPropose();
        e.setCle(cle);
        e.setType(type);
        e.setPromotionId(promotionId);
        e.setDatePrevue(datePrevue);
        e.setTitre(titre);
        e.setMessage(message);
        e.setStatut("PROPOSEE");
        em.persist(e);
        return 1;
    }

    /** Passe en EXPIREE les propositions non traitées dont la date est dépassée. */
    public int expirerDepassees() {
        return em.createQuery(
                "UPDATE EnvoiPropose e SET e.statut = 'EXPIREE', e.updatedAt = :now " +
                "WHERE e.statut = 'PROPOSEE' AND e.datePrevue < :today")
                .setParameter("now", LocalDateTime.now())
                .setParameter("today", LocalDate.now())
                .executeUpdate();
    }

    /* ====================== Décisions humaines ====================== */

    /**
     * Valide une proposition : crée une campagne en BROUILLON (aucun envoi
     * automatique) et lie la proposition à cette campagne.
     *
     * @param listeId   audience facultative (liste de diffusion) choisie à la validation
     * @param segmentId audience facultative (segment) choisie à la validation
     */
    public EnvoiPropose valider(Long id, Long listeId, Long segmentId) {
        EnvoiPropose e = em.find(EnvoiPropose.class, id);
        if (e == null) { throw new IllegalArgumentException("Proposition introuvable"); }
        if (!"PROPOSEE".equals(e.getStatut())) {
            throw new ValidationException("statut",
                    "Seule une proposition au statut « PROPOSEE » peut être validée.");
        }
        Campagne c = new Campagne();
        c.setNom(e.getTitre());
        c.setDescription(e.getMessage());
        c.setObjectif("Promotion");
        c.setCategorie("PROMOTION");
        c.setStatut("BROUILLON");
        c.setCanal("API");
        // L'envoi est programmé à 9h le jour prévu ; l'opérateur peut l'ajuster.
        c.setDateProgrammee(e.getDatePrevue().atTime(9, 0));
        if (listeId != null) { c.setListeId(listeId); }
        else if (e.getListeId() != null) { c.setListeId(e.getListeId()); }
        if (segmentId != null) { c.setSegmentId(segmentId); }
        else if (e.getSegmentId() != null) { c.setSegmentId(e.getSegmentId()); }
        campagneService.creer(c);

        e.setStatut("VALIDEE");
        e.setCampagneId(c.getId());
        e.setListeId(c.getListeId());
        e.setSegmentId(c.getSegmentId());
        return em.merge(e);
    }

    public EnvoiPropose rejeter(Long id, String motif) {
        EnvoiPropose e = em.find(EnvoiPropose.class, id);
        if (e == null) { throw new IllegalArgumentException("Proposition introuvable"); }
        if (!"PROPOSEE".equals(e.getStatut())) {
            throw new ValidationException("statut",
                    "Seule une proposition au statut « PROPOSEE » peut être rejetée.");
        }
        e.setStatut("REJETEE");
        e.setMotifRejet(motif);
        return em.merge(e);
    }

    /* ====================== Messages suggérés ====================== */

    private String messageLancement(Promotion p) {
        return "Bonne nouvelle ! La promotion « " + p.getNom() + " » démarre"
                + (p.getDateFin() != null ? " et se termine le " + p.getDateFin().toLocalDate() : "")
                + ". Profitez-en dès maintenant.";
    }

    private String messageRappel(Promotion p, int joursAvant, LocalDate fin) {
        String quand = joursAvant == 1 ? "demain" : "dans " + joursAvant + " jours";
        return "Dernière ligne droite : la promotion « " + p.getNom() + " » se termine "
                + quand + " (le " + fin + "). Ne passez pas à côté !";
    }
}
