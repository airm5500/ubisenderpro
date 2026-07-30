package com.ubisenderpro.service;

import javax.ejb.EJB;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import java.util.logging.Logger;

/**
 * Ordonnanceur du module Recouvrement : génère chaque matin (6h30) les
 * propositions de relance de l'assistant. Les décisions manuelles
 * (VALIDEE / REJETEE) ne sont jamais écrasées (une seule proposition en attente
 * par client).
 */
@Singleton
@Startup
public class RecouvrementScheduler {

    @javax.ejb.EJB
    private LicenceService licenceService;

    /** Licence expirée/absente (mode obligatoire) : les automatisations sont suspendues. */
    private boolean suspenduParLicence() {
        try { return licenceService.envoisBloques(); } catch (RuntimeException e) { return false; }
    }

    private static final Logger LOG = Logger.getLogger(RecouvrementScheduler.class.getName());

    @EJB
    private RecAssistantService assistantService;

    @Schedule(hour = "6", minute = "30", persistent = false)
    public void tickPropositions() {
        if (suspenduParLicence()) { return; }
        try {
            int n = assistantService.genererPropositions();
            if (n > 0) { LOG.info("Recouvrement : " + n + " proposition(s) de relance générée(s)."); }
        } catch (Exception e) {
            LOG.warning("Génération des propositions de recouvrement ignorée : " + e.getMessage());
        }
    }
}
