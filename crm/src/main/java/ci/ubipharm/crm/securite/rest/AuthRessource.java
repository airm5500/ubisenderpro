package ci.ubipharm.crm.securite.rest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.securite.rest.service.AuthService;
import ci.ubipharm.crm.securite.rest.service.dto.ConnexionDTO;
import ci.ubipharm.crm.securite.rest.service.dto.JetonCsrfDTO;
import ci.ubipharm.crm.securite.rest.service.dto.UtilisateurConnecteDTO;
import ci.ubipharm.crm.securite.rest.service.dto.UtilisateurPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthRessource {

    private final AuthService authService;

    @GetMapping("/csrf")
    public JetonCsrfDTO csrf(CsrfToken jeton) {
        return authService.jetonCsrf(jeton);
    }

    @PostMapping("/connexion")
    public UtilisateurConnecteDTO connexion(@Valid @RequestBody ConnexionDTO connexion,
                                            HttpServletRequest requete, HttpServletResponse reponse) {
        return authService.connecter(connexion, requete, reponse);
    }

    @GetMapping("/moi")
    public UtilisateurConnecteDTO moi(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return authService.utilisateurConnecte(principal);
    }
}
