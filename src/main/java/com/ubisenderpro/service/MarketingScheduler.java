package com.ubisenderpro.service;

import javax.ejb.EJB;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;

/**
 * Actualise automatiquement le statut des promotions selon leurs dates
 * (PROGRAMMEE -> ACTIVE -> INACTIVE), hors décisions manuelles (ANNULEE/ARCHIVEE).
 */
@Singleton
@Startup
public class MarketingScheduler {

    @EJB
    private PromotionService promotionService;

    @Schedule(hour = "*", minute = "*/15", persistent = false)
    public void tick() {
        promotionService.rafraichirStatuts();
    }
}
