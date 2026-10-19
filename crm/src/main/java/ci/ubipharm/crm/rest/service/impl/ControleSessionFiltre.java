package ci.ubipharm.crm.rest.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import ci.ubipharm.crm.rest.service.dto.UtilisateurPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Controle de chaque appel d'API d'un utilisateur connecte :
 * - compte desactive, ou droits changes par l'administrateur apres l'ouverture de la session : la session est fermee ;
 * - mot de passe provisoire : seules les routes /api/auth restent ouvertes, le temps de le remplacer.
 */
public class ControleSessionFiltre extends OncePerRequestFilter {

    public static final String CONNECTE_LE = "crm.connecte_le";

    private final EntityManager em;

    public ControleSessionFiltre(EntityManager em) {
        this.em = em;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        return !requete.getRequestURI().startsWith(requete.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
            throws ServletException, IOException {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || !(authentification.getPrincipal() instanceof UtilisateurPrincipal principal)) {
            suite.doFilter(requete, reponse);
            return;
        }
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select statut as statut, mot_de_passe_provisoire as provisoire, droits_revises_le as revisesLe
                from crm_utilisateur where id = :id
                """, Tuple.class).setParameter("id", principal.getId()).getResultList();
        HttpSession session = requete.getSession(false);
        if (lignes.isEmpty() || sessionPerimee(lignes.getFirst(), session)) {
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            refuser(reponse, HttpStatus.UNAUTHORIZED, "Votre session a été fermée : reconnectez-vous.");
            return;
        }
        Object provisoire = lignes.getFirst().get("provisoire");
        boolean motDePasseProvisoire = provisoire instanceof Boolean b ? b : provisoire instanceof Number n && n.intValue() != 0;
        if (motDePasseProvisoire && !requete.getRequestURI().startsWith(requete.getContextPath() + "/api/auth/")) {
            refuser(reponse, HttpStatus.FORBIDDEN, "Remplacez votre mot de passe provisoire pour continuer.");
            return;
        }
        suite.doFilter(requete, reponse);
    }

    private boolean sessionPerimee(Tuple compte, HttpSession session) {
        if (!"ACTIF".equals(compte.get("statut", String.class))) {
            return true;
        }
        LocalDateTime revisesLe = compte.get("revisesLe", LocalDateTime.class);
        if (revisesLe == null) {
            return false;
        }
        Object connecteLe = session == null ? null : session.getAttribute(CONNECTE_LE);
        return !(connecteLe instanceof LocalDateTime debut) || revisesLe.isAfter(debut);
    }

    private void refuser(HttpServletResponse reponse, HttpStatus statut, String message) throws IOException {
        reponse.setStatus(statut.value());
        reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        reponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        reponse.getWriter().write("{\"statut\":" + statut.value() + ",\"message\":\"" + message + "\"}");
    }
}
