package com.ubisenderpro.service;

import com.ubisenderpro.entity.WaBulkJob;

import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Envoi en masse asynchrone via WhatsApp Web, avec débit configurable
 * (attente entre messages + pause après N messages) pour limiter le risque
 * de bannissement.
 */
@Stateless
public class WaBulkSenderAsync {

    private static final Logger LOG = Logger.getLogger(WaBulkSenderAsync.class.getName());

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private WaBulkSenderTx tx;

    @Asynchronous
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void lancer(Long jobId) {
        WaBulkJob job = em.find(WaBulkJob.class, jobId);
        if (job == null) return;

        tx.marquerStatut(jobId, "EN_COURS");
        List<Long> ids = tx.idsEnAttente(jobId);
        LOG.info("Envoi en masse " + jobId + " : " + ids.size() + " destinataires.");

        int compteur = 0;
        for (Long destId : ids) {
            String statut = tx.statutJob(jobId);
            if ("SUSPENDUE".equals(statut) || "ANNULEE".equals(statut)) {
                LOG.info("Envoi en masse " + jobId + " interrompu (" + statut + ").");
                return;
            }
            try {
                tx.envoyer(destId);
                compteur++;
                pause(attenteMs(job));
                if (job.getPauseApres() > 0 && compteur % job.getPauseApres() == 0) {
                    pause(pauseMs(job));
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOG.warning("Echec envoi destinataire " + destId + " : " + e.getMessage());
            }
        }

        tx.marquerStatut(jobId, "TERMINEE");
        LOG.info("Envoi en masse " + jobId + " terminé.");
    }

    private long attenteMs(WaBulkJob j) { return secAleatoire(j.getAttenteMin(), j.getAttenteMax()) * 1000L; }
    private long pauseMs(WaBulkJob j) { return secAleatoire(j.getPauseMin(), j.getPauseMax()) * 1000L; }

    private int secAleatoire(int min, int max) {
        if (max < min) { int t = min; min = max; max = t; }
        if (min < 0) min = 0;
        return max <= min ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private void pause(long ms) throws InterruptedException {
        if (ms > 0) { Thread.sleep(ms); }
    }
}
