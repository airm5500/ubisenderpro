package com.ubisenderpro.service;

import javax.ejb.EJB;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;

/**
 * Clôture périodiquement les sessions sans présence (page fermée, perte de
 * connexion, redémarrage serveur) afin que l'historique des connexions reflète
 * la réalité (déconnexion + temps de travail renseignés).
 */
@Singleton
@Startup
public class SessionScheduler {

    /** Une session est considérée morte si inactive depuis plus de ce seuil (secondes). */
    private static final long SEUIL_SECONDES = 180;

    @EJB
    private ConnexionLogService connexionLogService;

    @Schedule(hour = "*", minute = "*/2", persistent = false)
    public void tick() {
        connexionLogService.fermerInactives(SEUIL_SECONDES);
    }
}
