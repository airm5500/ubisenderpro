package ci.ubipharm.crm.commun.rest.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.commun.rest.service.AgenceService;
import ci.ubipharm.crm.commun.rest.service.dto.AgenceDTO;
import ci.ubipharm.crm.securite.rest.service.PerimetreService;
import ci.ubipharm.crm.securite.rest.service.dto.PerimetreDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgenceServiceImpl implements AgenceService {

    private final EntityManager em;
    private final PerimetreService perimetreService;

    @Override
    @Transactional(readOnly = true)
    public List<AgenceDTO> listerVisibles() {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (!perimetre.isVisionGlobale() && perimetre.getAgenceIds().isEmpty()) {
            return List.of();
        }
        String sql = "select a.id as id, a.code as code, a.libelle as libelle from crm_agence a where a.statut = 'ACTIF'"
                + (perimetre.isVisionGlobale() ? "" : " and a.id in (:agences)")
                + " order by a.libelle";
        Query requete = em.createNativeQuery(sql, Tuple.class);
        if (!perimetre.isVisionGlobale()) {
            requete.setParameter("agences", perimetre.getAgenceIds());
        }
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
    }

    private AgenceDTO mapTupleToDto(Tuple tuple) {
        return AgenceDTO.builder()
                .id(tuple.get("id", Long.class))
                .code(tuple.get("code", String.class))
                .libelle(tuple.get("libelle", String.class))
                .build();
    }
}
