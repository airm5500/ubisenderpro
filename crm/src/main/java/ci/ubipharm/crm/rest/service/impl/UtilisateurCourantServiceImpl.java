package ci.ubipharm.crm.rest.service.impl;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.UtilisateurPrincipal;

@Service
public class UtilisateurCourantServiceImpl implements UtilisateurCourantService {

    @Override
    public UtilisateurPrincipal utilisateur() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || !(authentification.getPrincipal() instanceof UtilisateurPrincipal principal)) {
            throw new AccessDeniedException("Aucun utilisateur connecté");
        }
        return principal;
    }
}
