package ci.ubipharm.crm.securite.rest.service.impl;

import java.util.List;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Service;

import ci.ubipharm.crm.securite.rest.service.AuthService;
import ci.ubipharm.crm.securite.rest.service.JournalConnexionService;
import ci.ubipharm.crm.securite.rest.service.PerimetreService;
import ci.ubipharm.crm.securite.rest.service.dto.ConnexionDTO;
import ci.ubipharm.crm.securite.rest.service.dto.JetonCsrfDTO;
import ci.ubipharm.crm.securite.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.securite.rest.service.dto.UtilisateurConnecteDTO;
import ci.ubipharm.crm.securite.rest.service.dto.UtilisateurPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final JournalConnexionService journalConnexionService;
    private final PerimetreService perimetreService;

    @Override
    public JetonCsrfDTO jetonCsrf(CsrfToken jeton) {
        return JetonCsrfDTO.builder().entete(jeton.getHeaderName()).jeton(jeton.getToken()).build();
    }

    @Override
    public UtilisateurConnecteDTO connecter(ConnexionDTO connexion, HttpServletRequest requete, HttpServletResponse reponse) {
        String adresseIp = requete.getRemoteAddr();
        String navigateur = requete.getHeader("User-Agent");
        Authentication authentification;
        try {
            authentification = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(connexion.getLogin(), connexion.getMotDePasse()));
        } catch (AuthenticationException e) {
            journalConnexionService.enregistrerEchec(connexion.getLogin(), adresseIp, navigateur);
            // Meme message que le compte existe, soit desactive ou inconnu
            throw new BadCredentialsException("Echec de connexion");
        }

        // Nouvel identifiant de session apres connexion (fixation de session)
        if (requete.getSession(false) != null) {
            requete.changeSessionId();
        }
        SecurityContext contexte = SecurityContextHolder.createEmptyContext();
        contexte.setAuthentication(authentification);
        SecurityContextHolder.setContext(contexte);
        securityContextRepository.saveContext(contexte, requete, reponse);

        UtilisateurPrincipal principal = (UtilisateurPrincipal) authentification.getPrincipal();
        journalConnexionService.enregistrerSucces(principal.getId(), principal.getLogin(), adresseIp, navigateur);
        return utilisateurConnecte(principal);
    }

    @Override
    public UtilisateurConnecteDTO utilisateurConnecte(UtilisateurPrincipal principal) {
        List<String> droits = principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        PerimetreDTO perimetre = perimetreService.perimetre(principal.getId());
        return UtilisateurConnecteDTO.builder()
                .id(principal.getId())
                .login(principal.getLogin())
                .nomComplet(principal.getNomComplet())
                .roles(sansPrefixe(droits, UtilisateurDetailsServiceImpl.PREFIXE_ROLE))
                .modules(sansPrefixe(droits, UtilisateurDetailsServiceImpl.PREFIXE_MODULE))
                .visionGlobale(perimetre.isVisionGlobale())
                .agenceIds(perimetre.getAgenceIds())
                .build();
    }

    private List<String> sansPrefixe(List<String> droits, String prefixe) {
        return droits.stream().filter(d -> d.startsWith(prefixe)).map(d -> d.substring(prefixe.length())).toList();
    }
}
