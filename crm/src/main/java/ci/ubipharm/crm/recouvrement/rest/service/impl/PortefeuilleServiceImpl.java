package ci.ubipharm.crm.recouvrement.rest.service.impl;

import static ci.ubipharm.crm.recouvrement.rest.service.impl.RequetesRecouvrement.AGENCES_EN_COURS;
import static ci.ubipharm.crm.recouvrement.rest.service.impl.RequetesRecouvrement.AGENT_RECOUVREMENT;
import static ci.ubipharm.crm.recouvrement.rest.service.impl.RequetesRecouvrement.DELEGATION_EN_COURS;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.commun.rest.service.ParametreService;
import ci.ubipharm.crm.commun.rest.service.impl.ValeursImport;
import ci.ubipharm.crm.recouvrement.rest.service.PortefeuilleService;
import ci.ubipharm.crm.recouvrement.rest.service.dto.AgentDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.NumeroAgentDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.PortefeuilleClientDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.PortefeuilleCriteresDTO;
import ci.ubipharm.crm.securite.rest.service.JournalActionService;
import ci.ubipharm.crm.securite.rest.service.PerimetreService;
import ci.ubipharm.crm.securite.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.securite.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.securite.rest.service.dto.UtilisateurPrincipal;
import ci.ubipharm.crm.socle.dto.PageDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PortefeuilleServiceImpl implements PortefeuilleService {

    /** Profils qui voient toutes les relances de leur perimetre, et pas seulement un portefeuille. */
    private static final Set<String> ROLES_DIRECTION = Set.of("DROIT_PERIMETRE_AGENCES");

    /** Factures ouvertes : les avoirs ne sont pas relances. */
    private static final String FACTURES_OUVERTES = "f.client_id = c.id and f.nature = 'FACTURE' and f.solde <> 0";

    private static final String SELECT_PORTEFEUILLE = """
            select c.id as clientId, c.code as code, c.raison_sociale as raisonSociale, c.nom_pharmacien as nomPharmacien,
                   a.id as agenceId, a.libelle as agenceLibelle, c.segmentation as segmentation,
                   (select ct.numero_whatsapp from crm_contact ct
                     where ct.client_id = c.id and ct.rang = 1 and ct.statut = 'ACTIF' order by ct.id desc limit 1) as numeroPrincipal,
                   af.agent_id as agentId, ag.nom_complet as agentNom, af.date_debut as affecteLe,
                   (select count(*) from crm_facture f where %1$s) as nbFacturesOuvertes,
                   (select coalesce(sum(f.solde), 0) from crm_facture f where %1$s) as totalSolde,
                   (select coalesce(max(datediff(utc_date(), f.date_echeance)), 0) from crm_facture f
                     where %1$s and f.date_echeance < utc_date()) as retardMax
            """.formatted(FACTURES_OUVERTES);

    private static final String FROM_PORTEFEUILLE = """
            from crm_client c
            join crm_agence a on a.id = c.agence_id
            left join crm_affectation af on af.client_id = c.id and af.date_fin is null
            left join crm_utilisateur ag on ag.id = af.agent_id
            """;

    private static final String SELECT_AGENT = """
            select u.id as id, u.login as login, u.nom_complet as nomComplet, u.numero_whatsapp as numeroWhatsapp,
                   (select group_concat(ag.libelle order by ag.libelle separator ', ') from crm_agence ag
                     where ag.id in (%s)) as agences,
                   (select count(*) from crm_affectation af where af.agent_id = u.id and af.date_fin is null) as nbClients,
                   (select coalesce(sum(f.solde), 0) from crm_affectation af
                     join crm_facture f on f.client_id = af.client_id and f.nature = 'FACTURE'
                     where af.agent_id = u.id and af.date_fin is null) as totalSolde,
                   (select r.nom_complet from crm_delegation d join crm_utilisateur r on r.id = d.remplacant_id
                     where d.titulaire_id = u.id and %s order by d.date_debut limit 1) as remplacant
            from crm_utilisateur u
            """.formatted(AGENCES_EN_COURS, DELEGATION_EN_COURS);

    private final EntityManager em;
    private final PerimetreService perimetreService;
    private final UtilisateurCourantService utilisateurCourantService;
    private final ParametreService parametreService;
    private final JournalActionService journalActionService;

    @Override
    @Transactional(readOnly = true)
    public PageDTO<PortefeuilleClientDTO> rechercher(PortefeuilleCriteresDTO criteres) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        List<Long> agentsGeres = agentsGeres();
        StringBuilder where = new StringBuilder(" where c.statut = 'ACTIF'");
        Map<String, Object> parametres = new LinkedHashMap<>();
        if (agentsGeres != null) {
            // Un agent ne voit que son portefeuille et ceux qu'il remplace aujourd'hui
            where.append(" and af.agent_id in (:agentsGeres)");
            parametres.put("agentsGeres", agentsGeres);
        } else if (!perimetre.isVisionGlobale()) {
            if (perimetre.getAgenceIds().isEmpty()) {
                return PageDTO.<PortefeuilleClientDTO>builder().elements(List.of()).total(0).page(criteres.getPage()).taille(criteres.getTaille()).build();
            }
            where.append(" and c.agence_id in (:agencesVisibles)");
            parametres.put("agencesVisibles", perimetre.getAgenceIds());
        }
        if (criteres.getAgentId() != null) {
            where.append(" and af.agent_id = :agentId");
            parametres.put("agentId", criteres.getAgentId());
        }
        if (criteres.isSansAgent()) {
            where.append(" and af.id is null");
        }
        if (criteres.getAgenceId() != null) {
            where.append(" and c.agence_id = :agenceId");
            parametres.put("agenceId", criteres.getAgenceId());
        }
        if (criteres.isAvecSolde()) {
            where.append(" and exists (select 1 from crm_facture f where ").append(FACTURES_OUVERTES).append(")");
        }
        if (criteres.getQ() != null && !criteres.getQ().isBlank()) {
            where.append(" and (c.code like :q or c.raison_sociale like :q or c.nom_pharmacien like :q)");
            parametres.put("q", "%" + criteres.getQ().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }

        Query comptage = em.createNativeQuery("select count(*) " + FROM_PORTEFEUILLE + where);
        parametres.forEach(comptage::setParameter);
        long total = ((Number) comptage.getSingleResult()).longValue();

        Query requete = em.createNativeQuery(SELECT_PORTEFEUILLE + FROM_PORTEFEUILLE + where
                + " order by totalSolde desc, c.raison_sociale, c.id", Tuple.class);
        parametres.forEach(requete::setParameter);
        requete.setFirstResult(criteres.getPage() * criteres.getTaille());
        requete.setMaxResults(criteres.getTaille());
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return PageDTO.<PortefeuilleClientDTO>builder()
                .elements(lignes.stream().map(this::mapTupleToDto).toList())
                .total(total)
                .page(criteres.getPage())
                .taille(criteres.getTaille())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentDTO> agents(Long agenceId) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        StringBuilder where = new StringBuilder(" where ").append(AGENT_RECOUVREMENT);
        Map<String, Object> parametres = new LinkedHashMap<>();
        if (!perimetre.isVisionGlobale()) {
            if (perimetre.getAgenceIds().isEmpty()) {
                return List.of();
            }
            where.append(" and exists (").append(AGENCES_EN_COURS).append(" and ua.agence_id in (:agencesVisibles))");
            parametres.put("agencesVisibles", perimetre.getAgenceIds());
        }
        if (agenceId != null) {
            where.append(" and exists (").append(AGENCES_EN_COURS).append(" and ua.agence_id = :agenceId)");
            parametres.put("agenceId", agenceId);
        }
        Query requete = em.createNativeQuery(SELECT_AGENT + where + " order by u.nom_complet", Tuple.class);
        parametres.forEach(requete::setParameter);
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return lignes.stream().map(this::mapTupleToAgentDto).toList();
    }

    @Override
    @Transactional
    public AgentDTO enregistrerNumero(Long agentId, NumeroAgentDTO numero) {
        String normalise = ValeursImport.numeroWhatsapp(numero.getNumeroWhatsapp(), parametreService.valeur("whatsapp.prefixe_pays", "225"))
                .orElseThrow(() -> new ErreurMetierException("Numéro invalide : 10 chiffres attendus, par exemple 07 00 00 00 01."));
        AgentDTO agent = agent(agentId);
        @SuppressWarnings("unchecked")
        List<String> autres = em.createNativeQuery("select nom_complet from crm_utilisateur where numero_whatsapp = :numero and id <> :id")
                .setParameter("numero", normalise)
                .setParameter("id", agentId)
                .getResultList();
        if (!autres.isEmpty()) {
            throw new ErreurMetierException("Ce numéro est déjà celui de " + autres.getFirst() + ".");
        }
        em.createNativeQuery("update crm_utilisateur set numero_whatsapp = :numero, updated_at = utc_timestamp() where id = :id")
                .setParameter("numero", normalise)
                .setParameter("id", agentId)
                .executeUpdate();
        journalActionService.enregistrer("AGENT_NUMERO", "crm_utilisateur", agentId,
                (agent.getNumeroWhatsapp() == null ? "aucun" : agent.getNumeroWhatsapp()) + " -> " + normalise);
        return agent(agentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> agentsGeres() {
        UtilisateurPrincipal utilisateur = utilisateurCourantService.utilisateur();
        if (utilisateur.getAuthorities().stream().anyMatch(a -> ROLES_DIRECTION.contains(a.getAuthority()))) {
            return null;
        }
        List<Long> agents = new ArrayList<>(List.of(utilisateur.getId()));
        @SuppressWarnings("unchecked")
        List<Number> titulaires = em.createNativeQuery("select d.titulaire_id from crm_delegation d where d.remplacant_id = :id and "
                + DELEGATION_EN_COURS)
                .setParameter("id", utilisateur.getId())
                .getResultList();
        titulaires.forEach(t -> agents.add(t.longValue()));
        return agents;
    }

    /** Agent de recouvrement visible dans le perimetre, sinon introuvable. */
    private AgentDTO agent(Long agentId) {
        return agents(null).stream().filter(a -> a.getId().equals(agentId)).findFirst()
                .orElseThrow(() -> new RessourceIntrouvableException("Agent introuvable"));
    }

    PortefeuilleClientDTO mapTupleToDto(Tuple tuple) {
        return PortefeuilleClientDTO.builder()
                .clientId(tuple.get("clientId", Long.class))
                .code(tuple.get("code", String.class))
                .raisonSociale(tuple.get("raisonSociale", String.class))
                .nomPharmacien(tuple.get("nomPharmacien", String.class))
                .agenceId(tuple.get("agenceId", Long.class))
                .agenceLibelle(tuple.get("agenceLibelle", String.class))
                .segmentation(tuple.get("segmentation", String.class))
                .numeroPrincipal(tuple.get("numeroPrincipal", String.class))
                .agentId(tuple.get("agentId", Long.class))
                .agentNom(tuple.get("agentNom", String.class))
                .affecteLe(tuple.get("affecteLe", LocalDateTime.class))
                .nbFacturesOuvertes(((Number) tuple.get("nbFacturesOuvertes")).intValue())
                .totalSolde(tuple.get("totalSolde", BigDecimal.class))
                .retardMax(((Number) tuple.get("retardMax")).intValue())
                .build();
    }

    AgentDTO mapTupleToAgentDto(Tuple tuple) {
        return AgentDTO.builder()
                .id(tuple.get("id", Long.class))
                .login(tuple.get("login", String.class))
                .nomComplet(tuple.get("nomComplet", String.class))
                .numeroWhatsapp(tuple.get("numeroWhatsapp", String.class))
                .agences(tuple.get("agences", String.class))
                .nbClients(((Number) tuple.get("nbClients")).intValue())
                .totalSolde(tuple.get("totalSolde", BigDecimal.class))
                .remplacant(tuple.get("remplacant", String.class))
                .build();
    }
}
