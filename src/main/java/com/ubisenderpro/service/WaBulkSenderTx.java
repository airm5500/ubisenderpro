package com.ubisenderpro.service;

import com.ubisenderpro.entity.WaBulkDestinataire;
import com.ubisenderpro.entity.WaBulkJob;
import com.ubisenderpro.whatsapp.WaWebClient;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Étapes transactionnelles unitaires de l'envoi en masse WhatsApp Web.
 * Un envoi = une transaction (REQUIRES_NEW) avec rotation des variantes.
 */
@Stateless
public class WaBulkSenderTx {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    private final WaWebClient client = new WaWebClient();

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void marquerStatut(Long jobId, String statut) {
        WaBulkJob j = em.find(WaBulkJob.class, jobId);
        if (j != null) { j.setStatut(statut); em.merge(j); }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public String statutJob(Long jobId) {
        WaBulkJob j = em.find(WaBulkJob.class, jobId);
        return j == null ? null : j.getStatut();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public List<Long> idsEnAttente(Long jobId) {
        return em.createQuery(
                "SELECT d.id FROM WaBulkDestinataire d WHERE d.jobId = :j AND d.statut = 'EN_ATTENTE'",
                Long.class).setParameter("j", jobId).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void envoyer(Long destinataireId) {
        WaBulkDestinataire d = em.find(WaBulkDestinataire.class, destinataireId);
        if (d == null || !"EN_ATTENTE".equals(d.getStatut())) return;
        WaBulkJob job = em.find(WaBulkJob.class, d.getJobId());
        if (job == null) return;

        String sessionId = WaWebSessionService.nodeId(job.getSessionId());
        String texte = personnaliser(choisirVariante(job), d.getNom());

        WaWebClient.SendResult res;
        if (job.getMediaUrl() != null && !job.getMediaUrl().isEmpty()) {
            res = client.sendMedia(sessionId, d.getNumero(),
                    job.getMediaType() == null ? "image" : job.getMediaType(),
                    job.getMediaUrl(), texte, job.getMediaMime(), job.getMediaNom());
        } else {
            res = client.sendText(sessionId, d.getNumero(), texte);
        }

        if (res.success) {
            d.setStatut("ENVOYE");
            d.setSentAt(LocalDateTime.now());
            job.setEnvoyes(job.getEnvoyes() + 1);
        } else {
            d.setStatut("ECHEC");
            d.setErreur(res.erreur);
            job.setEchoues(job.getEchoues() + 1);
        }
        em.merge(d);
        em.merge(job);
    }

    /** Choisit aléatoirement une variante non vide parmi les 5 (anti-spam). */
    private String choisirVariante(WaBulkJob j) {
        List<String> variantes = new ArrayList<>();
        for (String v : new String[]{j.getMsg1(), j.getMsg2(), j.getMsg3(), j.getMsg4(), j.getMsg5()}) {
            if (v != null && !v.trim().isEmpty()) { variantes.add(v); }
        }
        if (variantes.isEmpty()) return "";
        return variantes.get(ThreadLocalRandom.current().nextInt(variantes.size()));
    }

    private String personnaliser(String texte, String nom) {
        if (texte == null) return "";
        String n = nom == null ? "" : nom;
        return texte.replace("[NAME]", n)
                .replace("{{nom}}", n)
                .replace("{{nom_contact}}", n)
                .replace("{{name}}", n);
    }
}
