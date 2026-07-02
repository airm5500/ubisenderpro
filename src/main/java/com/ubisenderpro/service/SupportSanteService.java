package com.ubisenderpro.service;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Santé de l'application (Centre de support) : sondes agrégées calculées à la
 * demande — base, WhatsApp API/Web, e-mail, JVM, compteurs d'incidents.
 */
@Stateless
public class SupportSanteService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private MailService mailService;
    @EJB
    private SupportEventService eventService;
    @EJB
    private SupportService supportService;

    public Map<String, Object> sante() {
        Map<String, Object> s = new LinkedHashMap<>();

        // Base de données : ping + latence.
        Map<String, Object> db = new LinkedHashMap<>();
        long t0 = System.nanoTime();
        try {
            em.createNativeQuery("SELECT 1").getSingleResult();
            db.put("ok", true);
            db.put("latenceMs", Math.round((System.nanoTime() - t0) / 1_000_000.0));
        } catch (RuntimeException e) {
            db.put("ok", false);
            db.put("erreur", e.getMessage());
        }
        s.put("base", db);

        // WhatsApp Cloud API : au moins un compte actif.
        Map<String, Object> wa = new LinkedHashMap<>();
        try {
            Number n = (Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM usp_whatsapp_account WHERE actif = 1").getSingleResult();
            wa.put("comptesActifs", n.longValue());
            wa.put("ok", n.longValue() > 0);
        } catch (RuntimeException e) { wa.put("ok", false); }
        s.put("whatsappApi", wa);

        // WhatsApp Web : sessions par statut.
        Map<String, Object> waweb = new LinkedHashMap<>();
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery(
                    "SELECT statut, COUNT(*) FROM usp_wa_web_session GROUP BY statut").getResultList();
            long connectees = 0, total = 0;
            for (Object[] r : rows) {
                long c = ((Number) r[1]).longValue();
                total += c;
                if ("CONNECTE".equals(String.valueOf(r[0]))) { connectees += c; }
            }
            waweb.put("sessions", total);
            waweb.put("connectees", connectees);
            waweb.put("ok", total == 0 || connectees > 0);
        } catch (RuntimeException e) { waweb.put("ok", false); }
        s.put("whatsappWeb", waweb);

        // E-mail SMTP.
        Map<String, Object> mail = new LinkedHashMap<>();
        mail.put("configure", mailService.estConfigure());
        mail.put("ok", mailService.estConfigure());
        s.put("email", mail);

        // Serveur / JVM.
        Map<String, Object> jvm = new LinkedHashMap<>();
        Runtime rt = Runtime.getRuntime();
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        jvm.put("uptimeMinutes", uptimeMs / 60000);
        jvm.put("memoireUtiliseeMo", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        jvm.put("memoireMaxMo", rt.maxMemory() / (1024 * 1024));
        jvm.put("heureServeur", LocalDateTime.now().toString());
        jvm.put("ok", true);
        s.put("serveur", jvm);

        // Compteurs d'exploitation.
        Map<String, Object> compteurs = new LinkedHashMap<>();
        try {
            compteurs.put("erreurs24h", eventService.compter("ERROR", 24));
            compteurs.put("erreurs7j", eventService.compter("ERROR", 24 * 7));
            compteurs.put("avertissements24h", eventService.compter("WARN", 24));
            compteurs.put("ticketsOuverts", supportService.ticketsOuverts());
        } catch (RuntimeException ignore) { }
        s.put("compteurs", compteurs);

        return s;
    }
}
