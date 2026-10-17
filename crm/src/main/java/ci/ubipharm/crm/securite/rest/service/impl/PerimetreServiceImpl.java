package ci.ubipharm.crm.securite.rest.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.securite.rest.service.PerimetreService;
import ci.ubipharm.crm.securite.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.securite.rest.service.dto.PerimetreDTO;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PerimetreServiceImpl implements PerimetreService {

    private final EntityManager em;
    private final UtilisateurCourantService utilisateurCourantService;

    @Override
    public PerimetreDTO perimetreCourant() {
        return perimetre(utilisateurCourantService.utilisateur().getId());
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
