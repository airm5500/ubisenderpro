package ci.ubipharm.crm.rest.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.JournalConnexionService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JournalConnexionServiceImpl implements JournalConnexionService {

    private static final int ECHECS_MAX_PAR_DEFAUT = 5;

    private final EntityManager em;

    @Override
    @Transactional
    public void enregistrerSucces(Long utilisateurId, String login, String adresseIp, String navigateur) {
        tracer(utilisateurId, login, true, adresseIp, navigateur);
        em.createNativeQuery("""
                update crm_utilisateur
                set echecs_connexion = 0, derniere_connexion = utc_timestamp()
                where id = :id
                """)
                .setParameter("id", utilisateurId)
                .executeUpdate();
    }

    // MariaDB applique les affectations de gauche a droite : le statut est calcule
    // avant l'increment du compteur.
    @Override
    @Transactional
    public void enregistrerEchec(String login, String adresseIp, String navigateur) {
        tracer(null, login, false, adresseIp, navigateur);
        em.createNativeQuery("""
                update crm_utilisateur
                set statut = case when echecs_connexion + 1 >= :max and statut = 'ACTIF' then 'DESACTIVE' else statut end,
                    echecs_connexion = echecs_connexion + 1
                where login = :login and statut <> 'ARCHIVE'
                """)
                .setParameter("max", echecsMax())
                .setParameter("login", login)
                .executeUpdate();
    }

    private void tracer(Long utilisateurId, String login, boolean succes, String adresseIp, String navigateur) {
        em.createNativeQuery("""
                insert into crm_journal_connexion (utilisateur_id, login, succes, adresse_ip, navigateur, created_at)
                values (:id, :login, :succes, :ip, :navigateur, utc_timestamp())
                """)
                .setParameter("id", utilisateurId)
                .setParameter("login", tronquer(login, 100))
                .setParameter("succes", succes)
                .setParameter("ip", tronquer(adresseIp, 60))
                .setParameter("navigateur", tronquer(navigateur, 500))
                .executeUpdate();
    }

    private int echecsMax() {
        @SuppressWarnings("unchecked")
        List<String> valeurs = em.createNativeQuery("select valeur from crm_parametre where cle = 'securite.echecs_max'")
                .getResultList();
        try {
            return valeurs.isEmpty() ? ECHECS_MAX_PAR_DEFAUT : Integer.parseInt(valeurs.getFirst().trim());
        } catch (NumberFormatException e) {
            return ECHECS_MAX_PAR_DEFAUT;
        }
    }

    private String tronquer(String valeur, int longueur) {
        return valeur == null || valeur.length() <= longueur ? valeur : valeur.substring(0, longueur);
    }
}
