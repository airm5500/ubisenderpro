package com.ubisenderpro.rest;

import com.ubisenderpro.security.Secured;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Indicateurs du tableau de bord disponibles en Phase 1 (section 6 de la spec).
 * Les indicateurs WhatsApp / campagnes arriveront avec les phases correspondantes.
 */
@Path("/dashboard")
@Secured
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @GET
    public Map<String, Object> indicateurs() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("comptesClients", compter("usp_client"));
        d.put("contacts", compter("usp_client_contact"));
        d.put("contactsWhatsapp", compter("usp_client_contact WHERE numero_whatsapp IS NOT NULL AND numero_whatsapp <> ''"));
        d.put("contactsSansWhatsapp", compter("usp_client_contact WHERE numero_whatsapp IS NULL OR numero_whatsapp = ''"));
        d.put("contactsConsentement", compter("usp_client_contact WHERE consentement_whatsapp = 1"));
        d.put("contactsDesabonnes", compter("usp_client_contact WHERE desabonne = 1"));
        d.put("articles", compter("usp_article"));
        d.put("articlesActifs", compter("usp_article WHERE actif = 1"));
        d.put("articlesRupture", compter("usp_article WHERE stock_disponible <= seuil_alerte"));
        d.put("conversationsOuvertes", compter("usp_conversation WHERE statut = 'OUVERTE'"));
        d.put("conversationsNonLues", compter("usp_conversation WHERE non_lu > 0"));
        d.put("messagesEnvoyes", compter("usp_message WHERE direction = 'SORTANT' AND note_interne = 0"));
        d.put("sessionsWebConnectees", compter("usp_wa_web_session WHERE statut = 'CONNECTE'"));
        d.put("campagnesEnCours", compter("usp_campagne WHERE statut = 'EN_COURS'"));
        d.put("campagnesTerminees", compter("usp_campagne WHERE statut = 'TERMINEE'"));
        d.put("envoisMasse", compter("usp_wa_bulk_job"));
        d.put("modeles", compter("usp_modele_message WHERE actif = 1"));
        d.put("commandes", compter("usp_commande"));
        d.put("opportunitesOuvertes", compter("usp_opportunite WHERE statut NOT IN ('PERDU','CLIENT_FIDELISE')"));
        d.put("imports", compter("usp_import"));
        // Indicateurs d'usage / adoption.
        d.put("connexionsAujourdhui", compter("usp_connexion_log WHERE connexion_at >= CURDATE()"));
        d.put("utilisateursActifs7j", compter("(SELECT DISTINCT utilisateur_id FROM usp_connexion_log " +
                "WHERE connexion_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)) t"));
        d.put("sessionsEnCours", compter("usp_connexion_log WHERE deconnexion_at IS NULL"));
        d.put("messagesEnvoyesAujourdhui", compter("usp_message WHERE direction = 'SORTANT' " +
                "AND note_interne = 0 AND created_at >= CURDATE()"));
        return d;
    }

    private long compter(String fromClause) {
        Object r = em.createNativeQuery("SELECT COUNT(*) FROM " + fromClause).getSingleResult();
        return ((Number) r).longValue();
    }

    /**
     * Séries journalières d'évolution sur N jours (défaut 30) :
     * campagnes, messages WhatsApp Web (masse), messages API, discussions Web.
     */
    @GET
    @Path("/series")
    @SuppressWarnings("unchecked")
    public java.util.List<Map<String, Object>> series(@javax.ws.rs.QueryParam("jours") Integer jours) {
        int n = (jours == null || jours <= 0) ? 30 : Math.min(jours, 180);
        java.time.LocalDate start = java.time.LocalDate.now().minusDays(n - 1);
        String s = start.toString() + " 00:00:00";

        java.util.LinkedHashMap<String, long[]> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) { map.put(start.plusDays(i).toString(), new long[4]); }

        remplir(map, "SELECT DATE(COALESCE(envoye_at, created_at)), COUNT(*) FROM usp_campagne_destinataire " +
                "WHERE COALESCE(envoye_at, created_at) >= '" + s + "' GROUP BY 1", 0);
        remplir(map, "SELECT DATE(sent_at), COUNT(*) FROM usp_wa_bulk_destinataire " +
                "WHERE sent_at >= '" + s + "' GROUP BY 1", 1);
        remplir(map, "SELECT DATE(m.created_at), COUNT(*) FROM usp_message m " +
                "JOIN usp_conversation c ON m.conversation_id = c.id " +
                "WHERE m.direction = 'SORTANT' AND m.note_interne = 0 AND c.canal = 'API' " +
                "AND m.created_at >= '" + s + "' GROUP BY 1", 2);
        remplir(map, "SELECT DATE(m.created_at), COUNT(*) FROM usp_message m " +
                "JOIN usp_conversation c ON m.conversation_id = c.id " +
                "WHERE m.direction = 'SORTANT' AND m.note_interne = 0 AND c.canal = 'WEB' " +
                "AND m.created_at >= '" + s + "' GROUP BY 1", 3);

        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Map.Entry<String, long[]> e : map.entrySet()) {
            Map<String, Object> r = new LinkedHashMap<>();
            long[] v = e.getValue();
            r.put("date", e.getKey().substring(5)); // mm-dd
            r.put("campagnes", v[0]);
            r.put("waweb", v[1]);
            r.put("api", v[2]);
            r.put("discussions", v[3]);
            out.add(r);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void remplir(Map<String, long[]> map, String sql, int idx) {
        try {
            java.util.List<Object[]> rows = em.createNativeQuery(sql).getResultList();
            for (Object[] row : rows) {
                long[] arr = map.get(String.valueOf(row[0]));
                if (arr != null) { arr[idx] = ((Number) row[1]).longValue(); }
            }
        } catch (Exception ignore) { /* série indisponible : reste à zéro */ }
    }
}
