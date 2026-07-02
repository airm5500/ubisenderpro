package com.ubisenderpro.service;

import com.ubisenderpro.entity.WaBulkJob;

import javax.ejb.EJB;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import java.util.logging.Logger;

/**
 * Planificateur des envois en masse WhatsApp Web : chaque minute, lance les
 * travaux planifiés dont l'heure est atteinte et dont la plage horaire est
 * ouverte. Gère aussi la reprise des travaux mis en pause hors plage horaire.
 */
@Singleton
@Startup
public class WaScheduler {

    @javax.ejb.EJB
    private LicenceService licenceService;

    /** Licence expirée/absente (mode obligatoire) : les automatisations sont suspendues. */
    private boolean suspenduParLicence() {
        try { return licenceService.envoisBloques(); } catch (RuntimeException e) { return false; }
    }

    private static final Logger LOG = Logger.getLogger(WaScheduler.class.getName());

    @EJB
    private WaBulkService bulkService;
    @EJB
    private WaBulkSenderAsync sender;
    @EJB
    private WaWarmupService warmupService;

    @Schedule(hour = "*", minute = "*", persistent = false)
    public void tick() {
        if (suspenduParLicence()) { return; }
        for (WaBulkJob j : bulkService.planifiesDus()) {
            if (WaBulkService.dansFenetre(j)) {
                LOG.info("Planificateur : lancement de l'envoi " + j.getId());
                sender.lancer(j.getId());
            }
        }
    }

    /** Réchauffeur : envoi progressif toutes les 3 minutes. */
    @Schedule(hour = "*", minute = "*/3", persistent = false)
    public void tickWarmup() {
        if (suspenduParLicence()) { return; }
        warmupService.tick();
    }
}
