package com.ubisenderpro.service;

import com.ubisenderpro.entity.ConnexionLog;
import com.ubisenderpro.security.SessionStore;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Journalisation des sessions utilisateur (connexions / déconnexions).
 */
@Stateless
public class ConnexionLogService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private SessionStore sessionStore;

    /** Enregistre une connexion. Best-effort : ne doit jamais bloquer l'authentification.
     *  @return l'id de la ligne créée (pour enrichir le lieu en asynchrone), ou null. */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Long ouvrir(Long utilisateurId, String login, String token, String ip, String poste, String lieu) {
        try {
            ConnexionLog c = new ConnexionLog();
            c.setUtilisateurId(utilisateurId);
            c.setLogin(login);
            c.setSessionToken(token);
            c.setIp(ip);
            c.setPoste(poste);
            c.setLieu(lieu);
            c.setConnexionAt(LocalDateTime.now());
            em.persist(c);
            em.flush();
            return c.getId();
        } catch (Exception ignore) { /* journalisation non bloquante */ return null; }
    }

    /** Clôture la session correspondant au jeton (déconnexion + temps de travail). */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void fermer(String token) {
        if (token == null || token.isEmpty()) { return; }
        try {
            List<ConnexionLog> ouverts = em.createQuery(
                    "SELECT c FROM ConnexionLog c WHERE c.sessionToken = :t AND c.deconnexionAt IS NULL " +
                    "ORDER BY c.connexionAt DESC", ConnexionLog.class)
                    .setParameter("t", token).setMaxResults(1).getResultList();
            if (ouverts.isEmpty()) { return; }
            ConnexionLog c = ouverts.get(0);
            LocalDateTime fin = LocalDateTime.now();
            c.setDeconnexionAt(fin);
            c.setDureeSecondes(Duration.between(c.getConnexionAt(), fin).getSeconds());
            em.merge(c);
        } catch (Exception ignore) { /* non bloquant */ }
    }

    /**
     * Clôture les sessions sans présence récente : jeton expiré/absent (déconnexion,
     * serveur redémarré) ou inactif au-delà du seuil. Met à jour le temps de travail.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int fermerInactives(long seuilSecondes) {
        int n = 0;
        try {
            List<ConnexionLog> ouverts = em.createQuery(
                    "SELECT c FROM ConnexionLog c WHERE c.deconnexionAt IS NULL", ConnexionLog.class)
                    .getResultList();
            LocalDateTime maintenant = LocalDateTime.now();
            for (ConnexionLog c : ouverts) {
                long inactif = sessionStore.inactifDepuisSecondes(c.getSessionToken());
                if (inactif < 0 || inactif > seuilSecondes) {
                    c.setDeconnexionAt(maintenant);
                    c.setDureeSecondes(Duration.between(c.getConnexionAt(), maintenant).getSeconds());
                    em.merge(c);
                    n++;
                }
            }
        } catch (Exception ignore) { /* non bloquant */ }
        return n;
    }

    public List<ConnexionLog> lister(int limit) {
        return em.createQuery("SELECT c FROM ConnexionLog c ORDER BY c.connexionAt DESC", ConnexionLog.class)
                .setMaxResults(limit <= 0 ? 200 : limit).getResultList();
    }
}
