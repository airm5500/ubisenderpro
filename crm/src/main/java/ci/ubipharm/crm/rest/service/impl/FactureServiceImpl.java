package ci.ubipharm.crm.rest.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.FactureService;
import ci.ubipharm.crm.rest.service.PortefeuilleService;
import ci.ubipharm.crm.rest.service.dto.FactureCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.FactureDTO;
import ci.ubipharm.crm.rest.service.dto.SousTypeDTO;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FactureServiceImpl implements FactureService {

    private static final String SELECT = """
            select f.id as id, f.reference as reference, c.id as clientId, c.code as clientCode,
                   c.raison_sociale as clientRaisonSociale, a.id as agenceId, a.libelle as agenceLibelle,
                   st.id as sousTypeId, st.libelle as sousTypeLibelle, f.nature as nature,
                   f.date_facture as dateFacture, f.date_echeance as dateEcheance, f.montant as montant, f.solde as solde,
                   f.statut as statut, greatest(datediff(utc_date(), f.date_echeance), 0) as joursRetard
            from crm_facture f
            join crm_client c on c.id = f.client_id
            join crm_agence a on a.id = c.agence_id
            join crm_sous_type_compte st on st.id = f.sous_type_id
            """;

    private static final String FROM_COMPTAGE = """
            select count(*) from crm_facture f
            join crm_client c on c.id = f.client_id
            """;

    private final EntityManager em;
    private final PerimetreService perimetreService;
    private final PortefeuilleService portefeuilleService;

    @Override
    @Transactional(readOnly = true)
    public PageDTO<FactureDTO> rechercher(FactureCriteresDTO criteres) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (!perimetre.isVisionGlobale() && perimetre.getAgenceIds().isEmpty()) {
            return PageDTO.<FactureDTO>builder().elements(List.of()).total(0).page(criteres.getPage()).taille(criteres.getTaille()).build();
        }

        // Le cloisonnement par agence du client est applique dans la requete
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> parametres = new LinkedHashMap<>();
        if (!perimetre.isVisionGlobale()) {
            where.append(" and c.agence_id in (:agencesVisibles)");
            parametres.put("agencesVisibles", perimetre.getAgenceIds());
        }
        List<Long> agentsGeres = portefeuilleService.agentsGeres();
        if (agentsGeres != null) {
            // Un agent ne voit que les factures de son portefeuille et de ceux qu'il remplace
            where.append(" and exists (select 1 from crm_affectation af where af.client_id = c.id and af.date_fin is null"
                    + " and af.agent_id in (:agentsGeres))");
            parametres.put("agentsGeres", agentsGeres);
        }
        if (criteres.getClientId() != null) {
            where.append(" and f.client_id = :clientId");
            parametres.put("clientId", criteres.getClientId());
        }
        if (criteres.getAgenceId() != null) {
            where.append(" and c.agence_id = :agenceId");
            parametres.put("agenceId", criteres.getAgenceId());
        }
        if (criteres.getSousTypeId() != null) {
            where.append(" and f.sous_type_id = :sousTypeId");
            parametres.put("sousTypeId", criteres.getSousTypeId());
        }
        if (criteres.getStatut() != null) {
            where.append(" and f.statut = :statut");
            parametres.put("statut", criteres.getStatut());
        }
        if (criteres.getNature() != null) {
            where.append(" and f.nature = :nature");
            parametres.put("nature", criteres.getNature());
        }
        if (criteres.getEcheanceDu() != null) {
            where.append(" and f.date_echeance >= :echeanceDu");
            parametres.put("echeanceDu", criteres.getEcheanceDu());
        }
        if (criteres.getEcheanceAu() != null) {
            where.append(" and f.date_echeance <= :echeanceAu");
            parametres.put("echeanceAu", criteres.getEcheanceAu());
        }
        if (criteres.isEnRetard()) {
            where.append(" and f.date_echeance < utc_date() and f.solde <> 0");
        }
        if (criteres.getQ() != null && !criteres.getQ().isBlank()) {
            where.append(" and (f.reference like :q or c.code like :q or c.raison_sociale like :q)");
            parametres.put("q", "%" + echapperLike(criteres.getQ().trim()) + "%");
        }

        Query comptage = em.createNativeQuery(FROM_COMPTAGE + where);
        parametres.forEach(comptage::setParameter);
        long total = ((Number) comptage.getSingleResult()).longValue();

        Query requete = em.createNativeQuery(SELECT + where + " order by f.date_echeance, f.reference", Tuple.class);
        parametres.forEach(requete::setParameter);
        requete.setFirstResult(criteres.getPage() * criteres.getTaille());
        requete.setMaxResults(criteres.getTaille());
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return PageDTO.<FactureDTO>builder()
                .elements(lignes.stream().map(this::mapTupleToDto).toList())
                .total(total)
                .page(criteres.getPage())
                .taille(criteres.getTaille())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SousTypeDTO> sousTypes() {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(
                "select id as id, code as code, libelle as libelle from crm_sous_type_compte where statut = 'ACTIF' order by libelle",
                Tuple.class).getResultList();
        return lignes.stream().map(this::mapTupleToSousTypeDto).toList();
    }

    FactureDTO mapTupleToDto(Tuple tuple) {
        return FactureDTO.builder()
                .id(tuple.get("id", Long.class))
                .reference(tuple.get("reference", String.class))
                .clientId(tuple.get("clientId", Long.class))
                .clientCode(tuple.get("clientCode", String.class))
                .clientRaisonSociale(tuple.get("clientRaisonSociale", String.class))
                .agenceId(tuple.get("agenceId", Long.class))
                .agenceLibelle(tuple.get("agenceLibelle", String.class))
                .sousTypeId(tuple.get("sousTypeId", Long.class))
                .sousTypeLibelle(tuple.get("sousTypeLibelle", String.class))
                .nature(tuple.get("nature", String.class))
                .dateFacture(tuple.get("dateFacture", LocalDate.class))
                .dateEcheance(tuple.get("dateEcheance", LocalDate.class))
                .montant(tuple.get("montant", BigDecimal.class))
                .solde(tuple.get("solde", BigDecimal.class))
                .statut(tuple.get("statut", String.class))
                .joursRetard(((Number) tuple.get("joursRetard")).intValue())
                .build();
    }

    SousTypeDTO mapTupleToSousTypeDto(Tuple tuple) {
        return SousTypeDTO.builder()
                .id(tuple.get("id", Long.class))
                .code(tuple.get("code", String.class))
                .libelle(tuple.get("libelle", String.class))
                .build();
    }

    private String echapperLike(String texte) {
        return texte.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
