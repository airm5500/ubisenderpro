package ci.ubipharm.crm.securite.rest.service;

import org.springframework.security.web.csrf.CsrfToken;

import ci.ubipharm.crm.securite.rest.service.dto.ChangementMotDePasseDTO;
import ci.ubipharm.crm.securite.rest.service.dto.ConnexionDTO;
import ci.ubipharm.crm.securite.rest.service.dto.JetonCsrfDTO;
import ci.ubipharm.crm.securite.rest.service.dto.UtilisateurConnecteDTO;
import ci.ubipharm.crm.securite.rest.service.dto.UtilisateurPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    JetonCsrfDTO jetonCsrf(CsrfToken jeton);

    UtilisateurConnecteDTO connecter(ConnexionDTO connexion, HttpServletRequest requete, HttpServletResponse reponse);

    UtilisateurConnecteDTO utilisateurConnecte(UtilisateurPrincipal principal);

    /** Mot de passe choisi par l'utilisateur connecte, a la place du provisoire ou de l'ancien. */
    void changerMotDePasse(ChangementMotDePasseDTO changement);
}
