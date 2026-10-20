package ci.ubipharm.crm.socle.support;

import java.io.IOException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.SupportDiagnosticService;
import ci.ubipharm.crm.rest.service.dto.UtilisateurPrincipal;
import ci.ubipharm.crm.rest.service.impl.SupportExperienceServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Requetes de l'API plus longues que support.lenteur_ms : signalees au diagnostic (origine LENTEUR), une ligne par adresse normalisee.
 * Sans cout sur le chemin normal : une mesure d'horloge ; le seuil n'est relu (au plus chaque minute) que pour une requete deja
 * longue d'au moins 1 s, et l'enregistrement part en arriere-plan.
 * Les services sont lus a la demande : un filtre se cree au demarrage du serveur web, avant la couche JPA.
 */
@Component
public class FiltreLenteurs extends OncePerRequestFilter {

    /** Plancher du parametre support.lenteur_ms. */
    static final long PLANCHER_MS = 1000;

    private final ObjectProvider<ParametreService> parametreService;
    private final ObjectProvider<SupportDiagnosticService> supportDiagnosticService;
    private final TachesSupport taches;
    private volatile long seuilMs = 3000;
    private volatile long seuilLuLe;

    public FiltreLenteurs(ObjectProvider<ParametreService> parametreService, ObjectProvider<SupportDiagnosticService> supportDiagnosticService,
            TachesSupport taches) {
        this.parametreService = parametreService;
        this.supportDiagnosticService = supportDiagnosticService;
        this.taches = taches;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        String uri = requete.getRequestURI();
        return !uri.startsWith("/api/") || uri.startsWith("/api/support/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine) throws ServletException, IOException {
        long debut = System.nanoTime();
        try {
            chaine.doFilter(requete, reponse);
        } finally {
            long duree = (System.nanoTime() - debut) / 1_000_000;
            if (duree >= PLANCHER_MS) {
                signalerSiLente(requete, reponse, duree);
            }
        }
    }

    private void signalerSiLente(HttpServletRequest requete, HttpServletResponse reponse, long duree) {
        try {
            long seuil = seuil();
            if (duree < seuil) {
                return;
            }
            String route = SupportExperienceServiceImpl.cleAppel(requete.getMethod() + " " + requete.getRequestURI());
            if (route == null) {
                return;
            }
            Long utilisateur = utilisateur();
            String detail = "Durée : " + duree + " ms (seuil " + seuil + " ms)\nStatut HTTP : " + reponse.getStatus()
                    + "\nAdresse : " + requete.getMethod() + " " + requete.getRequestURI();
            taches.lancer(() -> supportDiagnosticService.getObject().signaler("LENTEUR", "Requête lente", route, detail, route, utilisateur));
        } catch (RuntimeException e) {
            // la mesure ne doit jamais perturber la reponse
        }
    }

    private long seuil() {
        long maintenant = System.currentTimeMillis();
        if (maintenant - seuilLuLe > 60_000) {
            seuilLuLe = maintenant;
            seuilMs = Math.max(PLANCHER_MS, parametreService.getObject().entier("support.lenteur_ms", 3000));
        }
        return seuilMs;
    }

    private static Long utilisateur() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof UtilisateurPrincipal u ? u.getId() : null;
    }
}
