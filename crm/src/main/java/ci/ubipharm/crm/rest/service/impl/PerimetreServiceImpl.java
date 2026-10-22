package ci.ubipharm.crm.rest.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PerimetreServiceImpl implements PerimetreService {

    private final EntityManager em;
    private final UtilisateurCourantService utilisateurCourantService;

    /** En-tete pose par l'interface selon le module affiche. */
    static final String EN_TETE_MODULE = "X-Module";

    @Override
    public PerimetreDTO perimetreCourant() {
        PerimetreDTO perimetre = perimetre(utilisateurCourantService.utilisateur().getId());
        if (!perimetre.isVisionGlobale() && contexteMarketing()) {
            perimetre.setVisionGlobale(true);
        }
        return perimetre;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean contexteMarketing() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributs)) {
            return false;
        }
        String chemin = attributs.getRequest().getRequestURI();
        if (chemin.startsWith("/api/recouvrement/")) {
            return false;
        }
        Tuple modules = (Tuple) em.createNativeQuery("""
                select coalesce(sum(h.module_id is null or m.code = 'MARKETING'), 0) as marketing,
                       coalesce(sum(h.module_id is null or m.code = 'RECOUVREMENT'), 0) as recouvrement
                from crm_habilitation h
                join crm_role r on r.id = h.role_id and r.actif = true
                left join crm_module m on m.id = h.module_id
                where h.utilisateur_id = :id and h.actif = true
                """, Tuple.class).setParameter("id", utilisateurCourantService.utilisateur().getId()).getSingleResult();
        if (((Number) modules.get("marketing")).longValue() == 0) {
            return false;
        }
        // Sans le Recouvrement, tout ecran commun est vu depuis le Marketing
        return ((Number) modules.get("recouvrement")).longValue() == 0
                || chemin.startsWith("/api/marketing/")
                || "MARKETING".equals(attributs.getRequest().getHeader(EN_TETE_MODULE));
    }

    @Override
    @Transactional(readOnly = true)
    public PerimetreDTO perimetre(Long utilisateurId) {
        Number globales = (Number) em.createNativeQuery("""
                select count(*) from crm_habilitation h
                join crm_role r on r.id = h.role_id
                where h.utilisateur_id = :id and h.actif = true and r.actif = true and r.vision_globale = true
                """)
                .setParameter("id", utilisateurId)
                .getSingleResult();
        @SuppressWarnings("unchecked")
        List<Number> agences = em.createNativeQuery("""
                select ua.agence_id from crm_utilisateur_agence ua
                where ua.utilisateur_id = :id
                  and ua.date_debut <= utc_date()
                  and (ua.date_fin is null or ua.date_fin >= utc_date())
                order by ua.agence_id
                """)
                .setParameter("id", utilisateurId)
                .getResultList();
        return PerimetreDTO.builder()
                .utilisateurId(utilisateurId)
                .visionGlobale(globales.longValue() > 0)
                .agenceIds(agences.stream().map(Number::longValue).toList())
                .build();
    }
}
