package ci.ubipharm.crm.whatsapp.rest.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.recouvrement.rest.service.PortefeuilleService;
import ci.ubipharm.crm.securite.rest.service.PerimetreService;
import ci.ubipharm.crm.securite.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import ci.ubipharm.crm.whatsapp.rest.service.MessageRecuService;
import ci.ubipharm.crm.whatsapp.rest.service.dto.MessageRecuCriteresDTO;
import ci.ubipharm.crm.whatsapp.rest.service.dto.MessageRecuDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MessageRecuServiceImpl implements MessageRecuService {

    private static final String FROM = """
            from crm_message_recu r
            left join crm_utilisateur u on u.numero_whatsapp = r.numero_agent
            left join crm_client c on c.id = r.client_id
            left join crm_campagne_message m on m.id = r.campagne_message_id
            left join crm_facture f on f.id = m.facture_id
            left join crm_campagne cp on cp.id = m.campagne_id
            """;

    private static final String SELECT = """
            select r.id as id, r.numero_agent as numeroAgent, u.nom_complet as agentNom, r.numero_client as numeroClient,
                   r.nom_affiche as nomAffiche, r.client_id as clientId, c.code as clientCode, c.raison_sociale as clientRaisonSociale,
                   f.reference as factureReference, cp.nom as campagneNom, r.type as type, r.contenu as contenu, r.recu_le as recuLe
            """;

    private final EntityManager em;
    private final PerimetreService perimetreService;
    private final PortefeuilleService portefeuilleService;

    @Override
    @Transactional(readOnly = true)
    public PageDTO<MessageRecuDTO> rechercher(MessageRecuCriteresDTO criteres) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> parametres = new LinkedHashMap<>();
        List<Long> agentsGeres = portefeuilleService.agentsGeres();
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (agentsGeres != null) {
            where.append(" and u.id in (:agentsGeres)");
            parametres.put("agentsGeres", agentsGeres);
        } else if (!perimetre.isVisionGlobale()) {
            where.append(" and c.agence_id in (:agences)");
            parametres.put("agences", perimetre.getAgenceIds().isEmpty() ? List.of(0L) : perimetre.getAgenceIds());
        }
        if (criteres.getClientId() != null) {
            where.append(" and r.client_id = :clientId");
            parametres.put("clientId", criteres.getClientId());
        }
        if (criteres.getAgentId() != null) {
            where.append(" and u.id = :agentId");
            parametres.put("agentId", criteres.getAgentId());
        }
        Query comptage = em.createNativeQuery("select count(*) " + FROM + where);
        parametres.forEach(comptage::setParameter);
        long total = ((Number) comptage.getSingleResult()).longValue();

        Query requete = em.createNativeQuery(SELECT + FROM + where + " order by r.recu_le desc, r.id desc", Tuple.class);
        parametres.forEach(requete::setParameter);
        requete.setFirstResult(criteres.getPage() * criteres.getTaille());
        requete.setMaxResults(criteres.getTaille());
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return PageDTO.<MessageRecuDTO>builder()
                .elements(lignes.stream().map(this::mapTupleToDto).toList())
                .total(total)
                .page(criteres.getPage())
                .taille(criteres.getTaille())
                .build();
    }

    MessageRecuDTO mapTupleToDto(Tuple tuple) {
        return MessageRecuDTO.builder()
                .id(tuple.get("id", Long.class))
                .numeroAgent(tuple.get("numeroAgent", String.class))
                .agentNom(tuple.get("agentNom", String.class))
                .numeroClient(tuple.get("numeroClient", String.class))
                .nomAffiche(tuple.get("nomAffiche", String.class))
                .clientId(tuple.get("clientId", Long.class))
                .clientCode(tuple.get("clientCode", String.class))
                .clientRaisonSociale(tuple.get("clientRaisonSociale", String.class))
                .factureReference(tuple.get("factureReference", String.class))
                .campagneNom(tuple.get("campagneNom", String.class))
                .type(tuple.get("type", String.class))
                .contenu(tuple.get("contenu", String.class))
                .recuLe(tuple.get("recuLe", LocalDateTime.class))
                .build();
    }
}
