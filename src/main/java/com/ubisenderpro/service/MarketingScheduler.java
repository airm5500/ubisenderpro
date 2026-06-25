package com.ubisenderpro.service;

import javax.ejb.EJB;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;

/**
 * Ordonnanceur marketing :
 * <ul>
 *   <li>actualise le statut des promotions selon leurs dates (toutes les 15 min) ;</li>
 *   <li>génère/actualise les propositions d'envoi du calendrier (une fois par jour,
 *       le matin) et expire celles dont la date est dépassée.</li>
 * </ul>
 * Les décisions manuelles (ANNULEE/ARCHIVEE, propositions VALIDEE/REJETEE) ne sont
 * jamais écrasées.
 */
@Singleton
@Startup
public class MarketingScheduler {

    @EJB
    private PromotionService promotionService;
    @EJB
    private EnvoiProposeService envoiProposeService;

    /** Recalcul des statuts de promotions (réactif). */
    @Schedule(hour = "*", minute = "*/15", persistent = false)
    public void tickStatuts() {
        promotionService.rafraichirStatuts();
    }

    /** Génération quotidienne des propositions d'envoi (7h00). */
    @Schedule(hour = "7", minute = "0", persistent = false)
    public void tickPropositions() {
        envoiProposeService.genererPropositions();
        envoiProposeService.expirerDepassees();
    }
}
