package com.ubisenderpro.service;

import com.ubisenderpro.entity.MediaFichier;
import com.ubisenderpro.entity.SupportDemande;
import com.ubisenderpro.entity.SupportTicket;
import com.ubisenderpro.entity.SupportTicketMessage;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Centre de support : demandes « Me contacter » (e-mail éditeur + archivage)
 * et tickets (workflow + conversation). Tables isolées usp_support_*.
 */
@Stateless
public class SupportService {

    /** Statuts du workflow des tickets. */
    public static final List<String> STATUTS = Arrays.asList(
            "NOUVEAU", "OUVERT", "AFFECTE", "EN_COURS", "EN_ATTENTE_CLIENT",
            "RESOLU", "CLOTURE", "ANNULE");

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private MailService mailService;
    @EJB
    private MediaFichierService mediaFichierService;
    @EJB
    private ParametreService parametreService;

    /* ------------------------- Demandes « Me contacter » ------------------------- */

    /** Archive la demande puis l'envoie par e-mail au support éditeur (best-effort). */
    public SupportDemande creerDemande(SupportDemande d) {
        if (d.getObjet() == null || d.getObjet().trim().isEmpty()) {
            throw new ValidationException("objet", "L'objet de la demande est obligatoire.");
        }
        if (d.getCorps() == null || d.getCorps().trim().isEmpty()) {
            throw new ValidationException("corps", "Décrivez votre demande.");
        }
        String dest = parametreService.valeur("support.email", "");
        boolean envoyable = dest != null && !dest.trim().isEmpty() && mailService.estConfigure();
        d.setStatut(envoyable ? "ENVOYEE" : "ARCHIVEE");
        if (!envoyable) {
            d.setErreur("E-mail support non configuré (Paramètres > support.email) : demande archivée.");
        }
        em.persist(d);

        if (envoyable) {
            String corps = "Nouvelle demande de support UbiSmartCRM Pro\n\n"
                    + "De        : " + n(d.getNom()) + " (" + n(d.getEmail()) + ")\n"
                    + "Société   : " + n(d.getSociete()) + "\n"
                    + "Téléphone : " + n(d.getTelephone()) + "\n"
                    + "Utilisateur connecté : " + n(d.getCreePar()) + "\n\n"
                    + "----------------------------------------\n"
                    + d.getCorps();
            mailService.envoyerAvecPieces(Collections.singletonList(dest.trim()),
                    "[Support] " + d.getObjet(), corps, pieces(d.getPieces()));
            // Accusé de réception au demandeur (best-effort).
            if (d.getEmail() != null && d.getEmail().contains("@")) {
                mailService.envoyer(Collections.singletonList(d.getEmail().trim()),
                        "Votre demande de support a bien été reçue",
                        "Bonjour " + n(d.getNom()) + ",\n\n"
                        + "Nous avons bien reçu votre demande « " + d.getObjet() + " ».\n"
                        + "L'équipe support vous répondra dans les meilleurs délais.\n\n"
                        + "— Support UbiSmartCRM Pro");
            }
        }
        return d;
    }

    public List<SupportDemande> listerDemandes(int limit) {
        return em.createQuery("SELECT d FROM SupportDemande d ORDER BY d.createdAt DESC", SupportDemande.class)
                .setMaxResults(limit > 0 && limit <= 1000 ? limit : 200).getResultList();
    }

    /* ------------------------------- Tickets ------------------------------- */

    public SupportTicket creerTicket(SupportTicket t, String login) {
        if (t.getSujet() == null || t.getSujet().trim().isEmpty()) {
            throw new ValidationException("sujet", "Le sujet du ticket est obligatoire.");
        }
        t.setUtilisateur(login);
        t.setStatut("NOUVEAU");
        t.setNumero(prochainNumero());
        em.persist(t);
        em.flush();
        // Première ligne de conversation = la description (traçabilité).
        if (t.getDescription() != null && !t.getDescription().trim().isEmpty()) {
            ajouterMessage(t.getId(), "CLIENT", login, t.getDescription(), t.getPieces());
        }
        return t;
    }

    /** TCK-2026-0001, séquence par année. */
    private String prochainNumero() {
        int annee = Year.now().getValue();
        Long n = em.createQuery(
                "SELECT COUNT(t) FROM SupportTicket t WHERE t.numero LIKE :p", Long.class)
                .setParameter("p", "TCK-" + annee + "-%").getSingleResult();
        return String.format("TCK-%d-%04d", annee, n + 1);
    }

    public List<SupportTicket> listerTickets(String utilisateur, String statut, String q) {
        StringBuilder jpql = new StringBuilder("SELECT t FROM SupportTicket t WHERE 1=1");
        if (utilisateur != null) { jpql.append(" AND t.utilisateur = :u"); }
        if (statut != null && !statut.isEmpty()) { jpql.append(" AND t.statut = :s"); }
        if (q != null && !q.trim().isEmpty()) {
            jpql.append(" AND (LOWER(t.sujet) LIKE :q OR LOWER(t.numero) LIKE :q)");
        }
        jpql.append(" ORDER BY t.updatedAt DESC, t.createdAt DESC");
        var query = em.createQuery(jpql.toString(), SupportTicket.class);
        if (utilisateur != null) { query.setParameter("u", utilisateur); }
        if (statut != null && !statut.isEmpty()) { query.setParameter("s", statut.toUpperCase()); }
        if (q != null && !q.trim().isEmpty()) { query.setParameter("q", "%" + q.trim().toLowerCase() + "%"); }
        return query.setMaxResults(500).getResultList();
    }

    public SupportTicket ticket(Long id) { return em.find(SupportTicket.class, id); }

    /** Changement de statut, tracé dans la conversation. */
    public SupportTicket changerStatut(Long id, String statut, String auteur) {
        String s = statut == null ? "" : statut.trim().toUpperCase();
        if (!STATUTS.contains(s)) {
            throw new ValidationException("statut", "Statut inconnu : " + statut);
        }
        SupportTicket t = em.find(SupportTicket.class, id);
        if (t == null) { throw new ValidationException("id", "Ticket introuvable."); }
        String avant = t.getStatut();
        t.setStatut(s);
        ajouterMessage(id, "SYSTEME", auteur, "Statut : " + avant + " → " + s, null);
        return em.merge(t);
    }

    /** Affectation, tracée dans la conversation ; passe le ticket en AFFECTE si NOUVEAU/OUVERT. */
    public SupportTicket affecter(Long id, String affecteA, String auteur) {
        SupportTicket t = em.find(SupportTicket.class, id);
        if (t == null) { throw new ValidationException("id", "Ticket introuvable."); }
        t.setAffecteA(affecteA);
        if ("NOUVEAU".equals(t.getStatut()) || "OUVERT".equals(t.getStatut())) { t.setStatut("AFFECTE"); }
        ajouterMessage(id, "SYSTEME", auteur, "Affecté à : " + n(affecteA), null);
        return em.merge(t);
    }

    public SupportTicketMessage ajouterMessage(Long ticketId, String direction, String auteur,
                                               String corps, String pieces) {
        if (corps == null || corps.trim().isEmpty()) {
            throw new ValidationException("corps", "Le message est vide.");
        }
        SupportTicketMessage m = new SupportTicketMessage();
        m.setTicketId(ticketId);
        m.setDirection(direction == null ? "CLIENT" : direction);
        m.setAuteur(auteur);
        m.setCorps(corps);
        m.setPieces(pieces);
        em.persist(m);
        // Touch updated_at du ticket.
        SupportTicket t = em.find(SupportTicket.class, ticketId);
        if (t != null) { em.merge(t); }
        return m;
    }

    public List<SupportTicketMessage> messages(Long ticketId) {
        return em.createQuery(
                "SELECT m FROM SupportTicketMessage m WHERE m.ticketId = :t ORDER BY m.createdAt", SupportTicketMessage.class)
                .setParameter("t", ticketId).getResultList();
    }

    /** Compteur pour la santé : tickets non clos. */
    public long ticketsOuverts() {
        return em.createQuery(
                "SELECT COUNT(t) FROM SupportTicket t WHERE t.statut NOT IN ('CLOTURE','ANNULE')", Long.class)
                .getSingleResult();
    }

    /* ------------------------------ interne ------------------------------ */

    /** Pièces jointes : ids CSV -> contenus MediaFichier. */
    private List<MailService.PieceJointe> pieces(String csv) {
        List<MailService.PieceJointe> out = new ArrayList<>();
        if (csv == null || csv.trim().isEmpty()) { return out; }
        for (String s : csv.split(",")) {
            try {
                mediaFichierService.parId(Long.valueOf(s.trim())).ifPresent(m ->
                        out.add(new MailService.PieceJointe(m.getContenu(), m.getNomFichier(), m.getMimeType())));
            } catch (NumberFormatException ignore) { /* id invalide : ignoré */ }
        }
        return out;
    }

    private static String n(String s) { return s == null || s.isEmpty() ? "—" : s; }
}
