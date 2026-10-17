package ci.ubipharm.crm.securite.rest.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ci.ubipharm.crm.securite.rest.service.JournalActionService;
import ci.ubipharm.crm.securite.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.securite.rest.service.dto.UtilisateurPrincipal;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JournalActionServiceImpl implements JournalActionService {

    private final EntityManager em;
    private final UtilisateurCourantService utilisateurCourantService;

    @Override
    @Transactional
    public void enregistrer(String action, String entite, Long entiteId, String details) {
        UtilisateurPrincipal utilisateur = utilisateurCourantService.utilisateur();
        em.createNativeQuery("""
                insert into crm_journal_action (utilisateur_id, login, action, entite, entite_id, details, adresse_ip, created_at)
                values (:utilisateurId, :login, :action, :entite, :entiteId, :details, :ip, utc_timestamp())
                """)
                .setParameter("utilisateurId", utilisateur.getId())
                .setParameter("login", utilisateur.getLogin())
                .setParameter("action", action)
                .setParameter("entite", entite)
                .setParameter("entiteId", entiteId)
                .setParameter("details", details)
                .setParameter("ip", adresseIp())
                .executeUpdate();
    }

    private String adresseIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributs) {
            return attributs.getRequest().getRemoteAddr();
        }
        return null;
    }
}
