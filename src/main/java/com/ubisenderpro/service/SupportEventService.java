package com.ubisenderpro.service;

import com.ubisenderpro.entity.ApplicationEvent;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Journal d'événements applicatifs du Centre de support : point de collecte
 * unique avec <b>dédoublonnage par signature</b> et <b>throttling</b> anti-flood.
 *
 * <p>La signature est un hash court de {@code type + module + message normalisé}
 * (numéros, ids et dates volatils retirés) : une même erreur répétée incrémente
 * un compteur au lieu de remplir la base.</p>
 */
@Stateless
public class SupportEventService {

    /** Max de collectes par signature et par minute (anti-flood). */
    private static final int MAX_PAR_MINUTE = 10;
    /** Taille maximale du payload technique conservé. */
    private static final int MAX_PAYLOAD = 4000;

    /** Compteurs de throttling partagés (signature -> [minuteEpoch, compteur]). */
    private static final ConcurrentHashMap<String, long[]> THROTTLE = new ConcurrentHashMap<>();
    /** Purge périodique : au plus une fois par heure. */
    private static final AtomicInteger PURGE_TICK = new AtomicInteger();

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private ParametreService parametreService;

    /**
     * Collecte un événement (nouvelle transaction : ne participe jamais au
     * rollback de la transaction appelante — la capture d'un bug ne doit pas
     * faire échouer l'opération métier).
     *
     * @return l'événement créé/incrémenté, ou null si throttlé/refusé.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public ApplicationEvent collecter(String type, String module, String niveau,
                                      String message, String payload,
                                      String utilisateur, String urlOuEcran) {
        try {
            if (message == null || message.trim().isEmpty()) { return null; }
            String t = (type == null || type.isEmpty()) ? "AUTRE" : type.trim().toUpperCase();
            String sig = signature(t, module, message);

            if (throttle(sig)) { return null; }

            List<ApplicationEvent> ex = em.createQuery(
                    "SELECT e FROM ApplicationEvent e WHERE e.signature = :s", ApplicationEvent.class)
                    .setParameter("s", sig).setMaxResults(1).getResultList();
            if (!ex.isEmpty()) {
                ApplicationEvent e = ex.get(0);
                e.setOccurrences(e.getOccurrences() + 1);
                e.setLastSeenAt(LocalDateTime.now());
                if (utilisateur != null) { e.setUtilisateur(utilisateur); }
                return em.merge(e);
            }

            ApplicationEvent e = new ApplicationEvent();
            e.setType(t);
            e.setModule(tronquer(module, 50));
            e.setNiveau(niveau == null || niveau.isEmpty() ? "ERROR" : niveau.toUpperCase());
            e.setSignature(sig);
            e.setMessageCourt(tronquer(message.trim(), 500));
            e.setUtilisateur(tronquer(utilisateur, 100));
            e.setUrlOuEcran(tronquer(urlOuEcran, 255));
            e.setPayloadJson(tronquer(payload, MAX_PAYLOAD));
            em.persist(e);

            purgerSiNecessaire();
            return e;
        } catch (RuntimeException ignore) {
            // La collecte ne doit JAMAIS propager d'erreur à l'appelant.
            return null;
        }
    }

    public List<ApplicationEvent> lister(String niveau, String q, int limit) {
        StringBuilder jpql = new StringBuilder("SELECT e FROM ApplicationEvent e WHERE 1=1");
        if (niveau != null && !niveau.isEmpty()) { jpql.append(" AND e.niveau = :n"); }
        if (q != null && !q.trim().isEmpty()) {
            jpql.append(" AND (LOWER(e.messageCourt) LIKE :q OR LOWER(e.module) LIKE :q OR LOWER(e.type) LIKE :q)");
        }
        jpql.append(" ORDER BY e.lastSeenAt DESC");
        var query = em.createQuery(jpql.toString(), ApplicationEvent.class);
        if (niveau != null && !niveau.isEmpty()) { query.setParameter("n", niveau.toUpperCase()); }
        if (q != null && !q.trim().isEmpty()) { query.setParameter("q", "%" + q.trim().toLowerCase() + "%"); }
        return query.setMaxResults(limit > 0 && limit <= 1000 ? limit : 300).getResultList();
    }

    public ApplicationEvent parId(Long id) { return em.find(ApplicationEvent.class, id); }

    /** Rattache un événement à un ticket. */
    public void lierTicket(Long eventId, Long ticketId) {
        ApplicationEvent e = em.find(ApplicationEvent.class, eventId);
        if (e != null) { e.setTicketId(ticketId); em.merge(e); }
    }

    /** Supprime les événements plus vieux que la rétention configurée. */
    public int purger() {
        int jours = 90;
        try { jours = Integer.parseInt(parametreService.valeur("support.retention_jours", "90").trim()); }
        catch (RuntimeException ignore) { }
        if (jours <= 0) { jours = 90; }
        return em.createQuery("DELETE FROM ApplicationEvent e WHERE e.lastSeenAt < :lim")
                .setParameter("lim", LocalDateTime.now().minusDays(jours))
                .executeUpdate();
    }

    /** Compteurs pour la santé : événements par niveau sur une fenêtre en heures. */
    public long compter(String niveau, int heures) {
        return em.createQuery(
                "SELECT COUNT(e) FROM ApplicationEvent e WHERE e.niveau = :n AND e.lastSeenAt >= :d", Long.class)
                .setParameter("n", niveau)
                .setParameter("d", LocalDateTime.now().minusHours(heures))
                .getSingleResult();
    }

    /* ------------------------------ interne ------------------------------ */

    /** Hash court de type+module+message normalisé (sans nombres/ids volatils). */
    static String signature(String type, String module, String message) {
        String norm = (message == null ? "" : message)
                .replaceAll("[0-9]+", "#")           // numéros de ligne, ids, dates
                .replaceAll("\\s+", " ")
                .toLowerCase();
        if (norm.length() > 300) { norm = norm.substring(0, 300); }
        String base = type + "|" + (module == null ? "" : module.toLowerCase()) + "|" + norm;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] h = md.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) { sb.append(String.format("%02x", h[i])); }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(base.hashCode());
        }
    }

    /** true si la signature a dépassé son quota sur la minute courante. */
    private static boolean throttle(String sig) {
        long minute = System.currentTimeMillis() / 60000L;
        long[] etat = THROTTLE.compute(sig, (k, v) ->
                (v == null || v[0] != minute) ? new long[]{minute, 1} : new long[]{minute, v[1] + 1});
        if (THROTTLE.size() > 5000) { THROTTLE.clear(); } // garde-fou mémoire
        return etat[1] > MAX_PAR_MINUTE;
    }

    /** Purge la rétention au plus toutes les ~200 collectes (coût amorti). */
    private void purgerSiNecessaire() {
        if (PURGE_TICK.incrementAndGet() % 200 == 1) {
            try { purger(); } catch (RuntimeException ignore) { }
        }
    }

    private static String tronquer(String s, int max) {
        if (s == null) { return null; }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
