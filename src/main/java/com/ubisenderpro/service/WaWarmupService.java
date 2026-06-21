package com.ubisenderpro.service;

import com.ubisenderpro.entity.WaWarmup;
import com.ubisenderpro.whatsapp.WaWebClient;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Réchauffeur (warming) des sessions WhatsApp Web : envoie progressivement de
 * petits messages neutres vers un pool de numéros, avec montée en charge
 * quotidienne, espacement et plage horaire « humaine ».
 */
@Stateless
public class WaWarmupService {

    private static final Logger LOG = Logger.getLogger(WaWarmupService.class.getName());
    private static final int HEURE_DEBUT = 8, HEURE_FIN = 20;
    private static final String[] PHRASES = {
            "Bonjour", "Salut, comment ça va ?", "Bonne journée à toi", "Coucou",
            "Tout va bien ?", "Hello", "Bonsoir", "Merci et à bientôt", "Au plaisir"
    };

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    private final WaWebClient client = new WaWebClient();

    public WaWarmup config(Long sessionId) {
        List<WaWarmup> l = em.createQuery("SELECT w FROM WaWarmup w WHERE w.sessionId = :s", WaWarmup.class)
                .setParameter("s", sessionId).setMaxResults(1).getResultList();
        return l.isEmpty() ? null : l.get(0);
    }

    public WaWarmup enregistrer(Long sessionId, WaWarmup data) {
        WaWarmup w = config(sessionId);
        if (w == null) { w = new WaWarmup(); w.setSessionId(sessionId); }
        w.setActif(data.isActif());
        w.setNumeros(data.getNumeros());
        if (data.getParJourBase() > 0) w.setParJourBase(data.getParJourBase());
        if (data.getParJourMax() > 0) w.setParJourMax(data.getParJourMax());
        if (data.getIncrementJour() >= 0) w.setIncrementJour(data.getIncrementJour());
        return w.getId() == null ? persist(w) : em.merge(w);
    }

    private WaWarmup persist(WaWarmup w) { em.persist(w); return w; }

    /** Appelé périodiquement par le planificateur : envoie au plus un message par session. */
    public void tick() {
        List<WaWarmup> actifs = em.createQuery("SELECT w FROM WaWarmup w WHERE w.actif = true", WaWarmup.class)
                .getResultList();
        for (WaWarmup w : actifs) {
            try { traiter(w); } catch (Exception e) { LOG.warning("Warmup " + w.getSessionId() + " : " + e.getMessage()); }
        }
    }

    private void traiter(WaWarmup w) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        // Nouveau jour : montée en charge + remise à zéro du compteur.
        if (w.getDateJour() == null || !today.equals(w.getDateJour())) {
            if (w.getDateJour() != null) { w.setJourCourant(w.getJourCourant() + 1); }
            w.setDateJour(today);
            w.setEnvoyesJour(0);
        }
        int quota = Math.min(w.getParJourMax(), w.getParJourBase() + (w.getJourCourant() - 1) * w.getIncrementJour());
        int h = LocalTime.now().getHour();
        boolean peut = w.getEnvoyesJour() < quota && h >= HEURE_DEBUT && h < HEURE_FIN;

        if (peut && w.getDernierEnvoi() != null) {
            long gapMin = Math.max(2, 720 / Math.max(1, quota)); // étale le quota sur ~12 h
            if (Duration.between(w.getDernierEnvoi(), now).toMinutes() < gapMin) { peut = false; }
        }

        List<String> pool = pool(w.getNumeros());
        if (peut && !pool.isEmpty()) {
            String numero = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            String phrase = PHRASES[ThreadLocalRandom.current().nextInt(PHRASES.length)];
            WaWebClient.SendResult r = client.sendText(WaWebSessionService.nodeId(w.getSessionId()), numero, phrase);
            if (r.success) {
                w.setEnvoyesJour(w.getEnvoyesJour() + 1);
                w.setDernierEnvoi(now);
                LOG.info("Warmup session " + w.getSessionId() + " : " + w.getEnvoyesJour() + "/" + quota);
            }
        }
        em.merge(w);
    }

    private List<String> pool(String numeros) {
        List<String> out = new ArrayList<>();
        if (numeros == null) { return out; }
        for (String l : numeros.split("\\r?\\n")) {
            String d = l.replaceAll("[^0-9]", "");
            if (d.length() >= 6) { out.add(d); }
        }
        return out;
    }
}
