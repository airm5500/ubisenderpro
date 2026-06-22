package com.ubisenderpro.service;

import com.ubisenderpro.entity.Campagne;

import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.logging.Logger;

/**
 * Lancement asynchrone des campagnes (section 21.1 étape 4 : lots, pause, débit).
 * Le traitement s'exécute hors de la requête HTTP : l'API /launch répond
 * immédiatement et la campagne progresse en arrière-plan.
 */
@Stateless
public class CampagneSenderAsync {

    private static final Logger LOG = Logger.getLogger(CampagneSenderAsync.class.getName());

    /** Pause entre deux envois, en millisecondes (limitation de débit). */
    private static final long PAUSE_MS = 200L;

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private CampagneSenderTx tx;

    @Asynchronous
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void lancer(Long campagneId) {
        Campagne c = em.find(Campagne.class, campagneId);
        if (c == null) return;
        boolean web = "WEB".equalsIgnoreCase(c.getCanal());
        if (c.getModeleId() == null
                || (web && (c.getWaWebSessionId() == null || c.getWaWebSessionId().isEmpty()))
                || (!web && c.getWhatsappAccountId() == null)) {
            tx.marquerStatut(campagneId, "ECHOUEE");
            LOG.warning("Campagne " + campagneId + " : "
                    + (web ? "session WhatsApp Web" : "compte WhatsApp") + " ou modèle manquant.");
            return;
        }

        tx.marquerStatut(campagneId, "EN_COURS");
        List<Long> ids = tx.idsEnAttente(campagneId);
        LOG.info("Campagne " + campagneId + " : envoi de " + ids.size() + " destinataires.");

        for (Long destId : ids) {
            // Permet une suspension de la campagne en cours de route.
            Campagne courante = em.find(Campagne.class, campagneId);
            if (courante == null || "SUSPENDUE".equals(courante.getStatut())
                    || "ANNULEE".equals(courante.getStatut())) {
                LOG.info("Campagne " + campagneId + " interrompue (" + (courante == null ? "supprimée" : courante.getStatut()) + ").");
                return;
            }
            try {
                tx.envoyer(destId, c.getWhatsappAccountId(), c.getModeleId());
                Thread.sleep(PAUSE_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOG.warning("Echec d'envoi destinataire " + destId + " : " + e.getMessage());
            }
        }

        tx.marquerStatut(campagneId, "TERMINEE");
        LOG.info("Campagne " + campagneId + " terminée.");
    }
}
