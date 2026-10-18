package ci.ubipharm.crm.recouvrement.rest.service.impl;

import static ci.ubipharm.crm.recouvrement.rest.service.impl.RequetesRecouvrement.AGENCES_EN_COURS;
import static ci.ubipharm.crm.recouvrement.rest.service.impl.RequetesRecouvrement.AGENT_RECOUVREMENT;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.recouvrement.rest.service.AffectationService;
import ci.ubipharm.crm.recouvrement.rest.service.dto.AffectationDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.AffectationDemandeDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.ResultatAffectationDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.RetraitAffectationDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.TransfertPortefeuilleDTO;
import ci.ubipharm.crm.securite.rest.service.JournalActionService;
import ci.ubipharm.crm.securite.rest.service.PerimetreService;
import ci.ubipharm.crm.securite.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

/**
 * Un client est confie a un seul agent a la fois. Une reaffectation clot l'affectation en cours au lieu
 * de la remplacer : l'historique dit qui suivait le client a chaque date.
 */
@Service
@RequiredArgsConstructor
public class AffectationServiceImpl implements AffectationService {

    private final EntityManager em;
    private final PerimetreService perimetreService;
    private final JournalActionService journalActionService;

    @Override
    @Transactional
    public ResultatAffectationDTO affecter(AffectationDemandeDTO demande) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        Agent agent = agent(demande.getAgentId(), perimetre);
        ResultatAffectationDTO resultat = ResultatAffectationDTO.builder().build();
        Map<Long, ClientCourant> clients = verrouillerClients(demande.getClientIds());
        for (Long clientId : new LinkedHashSet<>(demande.getClientIds())) {
            ClientCourant client = clients.get(clientId);
            if (client == null || !dansPerimetre(perimetre, client.agenceId())) {
                resultat.getRefus().add("Client n° " + clientId + " : introuvable.");
            } else if (!"ACTIF".equals(client.statut())) {
                resultat.getRefus().add(client.code() + " : client archivé.");
            } else if (!agent.agenceIds().contains(client.agenceId())) {
                resultat.getRefus().add(client.code() + " : client de l'agence " + client.agenceLibelle() + ", hors des agences de "
                        + agent.nom() + ".");
            } else if (agent.id().equals(client.agentId())) {
                resultat.setNbInchanges(resultat.getNbInchanges() + 1);
            } else {
                clore(clientId, motifReaffectation(demande.getMotif(), agent.nom()), perimetre.getUtilisateurId());
                ouvrir(clientId, agent.id(), demande.getMotif(), perimetre.getUtilisateurId());
                resultat.setNbTraites(resultat.getNbTraites() + 1);
            }
        }
        journalActionService.enregistrer("AFFECTATION", "crm_utilisateur", agent.id(),
                resultat.getNbTraites() + " clients confies a " + agent.nom() + ", " + resultat.getRefus().size() + " refuses");
        return resultat;
    }

    @Override
    @Transactional
    public ResultatAffectationDTO retirer(RetraitAffectationDTO retrait) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        ResultatAffectationDTO resultat = ResultatAffectationDTO.builder().build();
        Map<Long, ClientCourant> clients = verrouillerClients(retrait.getClientIds());
        for (Long clientId : new LinkedHashSet<>(retrait.getClientIds())) {
            ClientCourant client = clients.get(clientId);
            if (client == null || !dansPerimetre(perimetre, client.agenceId())) {
                resultat.getRefus().add("Client n° " + clientId + " : introuvable.");
            } else if (client.agentId() == null) {
                resultat.setNbInchanges(resultat.getNbInchanges() + 1);
            } else {
                clore(clientId, retrait.getMotif() == null ? "Retrait du portefeuille" : retrait.getMotif(), perimetre.getUtilisateurId());
                resultat.setNbTraites(resultat.getNbTraites() + 1);
            }
        }
        journalActionService.enregistrer("AFFECTATION_RETRAIT", "crm_client", null, resultat.getNbTraites() + " clients retires");
        return resultat;
    }

    @Override
    @Transactional
    public ResultatAffectationDTO transferer(Long agentId, TransfertPortefeuilleDTO transfert) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        Agent depart = agent(agentId, perimetre);
        if (agentId.equals(transfert.getVersAgentId())) {
            throw new ErreurMetierException("Choisissez un autre agent pour reprendre le portefeuille.");
        }
        @SuppressWarnings("unchecked")
        List<Number> clientIds = em.createNativeQuery("select client_id from crm_affectation where agent_id = :agentId and date_fin is null")
                .setParameter("agentId", agentId)
                .getResultList();
        if (clientIds.isEmpty()) {
            throw new ErreurMetierException("Le portefeuille de " + depart.nom() + " est vide.");
        }
        String motif = transfert.getMotif() == null ? "Transfert du portefeuille de " + depart.nom() : transfert.getMotif();
        return affecter(AffectationDemandeDTO.builder()
                .clientIds(clientIds.stream().map(Number::longValue).toList())
                .agentId(transfert.getVersAgentId())
                .motif(motif)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AffectationDTO> historique(Long clientId) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        @SuppressWarnings("unchecked")
        List<Number> agences = em.createNativeQuery("select agence_id from crm_client where id = :id").setParameter("id", clientId).getResultList();
        if (agences.isEmpty() || !dansPerimetre(perimetre, agences.getFirst().longValue())) {
            throw new RessourceIntrouvableException("Client introuvable");
        }
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select af.id as id, af.client_id as clientId, af.agent_id as agentId, ag.nom_complet as agentNom,
                       af.date_debut as dateDebut, af.date_fin as dateFin, af.motif_debut as motifDebut, af.motif_fin as motifFin,
                       p.nom_complet as affectePar, r.nom_complet as retirePar
                from crm_affectation af
                join crm_utilisateur ag on ag.id = af.agent_id
                join crm_utilisateur p on p.id = af.affecte_par
                left join crm_utilisateur r on r.id = af.retire_par
                where af.client_id = :clientId
                order by af.date_debut desc, af.id desc
                """, Tuple.class).setParameter("clientId", clientId).getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
    }

    /** L'agent doit etre un agent de recouvrement actif, et partager une agence avec un directeur d'agence. */
    private Agent agent(Long agentId, PerimetreDTO perimetre) {
        @SuppressWarnings("unchecked")
        List<String> noms = em.createNativeQuery("select u.nom_complet from crm_utilisateur u where u.id = :id and " + AGENT_RECOUVREMENT)
                .setParameter("id", agentId)
                .getResultList();
        if (noms.isEmpty()) {
            throw new ErreurMetierException("Cet utilisateur n'est pas un agent de recouvrement actif.");
        }
        @SuppressWarnings("unchecked")
        List<Number> agences = em.createNativeQuery(AGENCES_EN_COURS.replace("u.id", ":id")).setParameter("id", agentId).getResultList();
        Set<Long> agenceIds = new LinkedHashSet<>(agences.stream().map(Number::longValue).toList());
        if (!perimetre.isVisionGlobale() && agenceIds.stream().noneMatch(perimetre.getAgenceIds()::contains)) {
            throw new RessourceIntrouvableException("Agent introuvable");
        }
        return new Agent(agentId, noms.getFirst(), agenceIds);
    }

    /** Verrouille les clients concernes pour que deux affectations simultanees ne se croisent pas. */
    private Map<Long, ClientCourant> verrouillerClients(List<Long> clientIds) {
        Map<Long, ClientCourant> clients = new HashMap<>();
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(clientIds));
        for (int debut = 0; debut < ids.size(); debut += 1000) {
            @SuppressWarnings("unchecked")
            List<Tuple> lignes = em.createNativeQuery("""
                    select c.id as id, c.code as code, c.statut as statut, c.agence_id as agenceId, a.libelle as agenceLibelle,
                           (select af.agent_id from crm_affectation af where af.client_id = c.id and af.date_fin is null) as agentId
                    from crm_client c join crm_agence a on a.id = c.agence_id
                    where c.id in (:ids)
                    for update
                    """, Tuple.class).setParameter("ids", ids.subList(debut, Math.min(debut + 1000, ids.size()))).getResultList();
            for (Tuple t : lignes) {
                clients.put(t.get("id", Long.class), new ClientCourant(t.get("code", String.class), t.get("statut", String.class),
                        t.get("agenceId", Long.class), t.get("agenceLibelle", String.class), t.get("agentId", Long.class)));
            }
        }
        return clients;
    }

    private void clore(Long clientId, String motif, Long utilisateurId) {
        em.createNativeQuery("""
                update crm_affectation set date_fin = utc_timestamp(), motif_fin = :motif, retire_par = :utilisateurId
                where client_id = :clientId and date_fin is null
                """)
                .setParameter("motif", motif)
                .setParameter("utilisateurId", utilisateurId)
                .setParameter("clientId", clientId)
                .executeUpdate();
    }

    private void ouvrir(Long clientId, Long agentId, String motif, Long utilisateurId) {
        em.createNativeQuery("""
                insert into crm_affectation (client_id, agent_id, date_debut, motif_debut, affecte_par)
                values (:clientId, :agentId, utc_timestamp(), :motif, :utilisateurId)
                """)
                .setParameter("clientId", clientId)
                .setParameter("agentId", agentId)
                .setParameter("motif", motif)
                .setParameter("utilisateurId", utilisateurId)
                .executeUpdate();
    }

    private String motifReaffectation(String motif, String agent) {
        return motif == null ? "Reaffecte a " + agent : motif;
    }

    private boolean dansPerimetre(PerimetreDTO perimetre, Long agenceId) {
        return perimetre.isVisionGlobale() || perimetre.getAgenceIds().contains(agenceId);
    }

    AffectationDTO mapTupleToDto(Tuple tuple) {
        return AffectationDTO.builder()
                .id(tuple.get("id", Long.class))
                .clientId(tuple.get("clientId", Long.class))
                .agentId(tuple.get("agentId", Long.class))
                .agentNom(tuple.get("agentNom", String.class))
                .dateDebut(tuple.get("dateDebut", LocalDateTime.class))
                .dateFin(tuple.get("dateFin", LocalDateTime.class))
                .motifDebut(tuple.get("motifDebut", String.class))
                .motifFin(tuple.get("motifFin", String.class))
                .affectePar(tuple.get("affectePar", String.class))
                .retirePar(tuple.get("retirePar", String.class))
                .build();
    }

    record Agent(Long id, String nom, Set<Long> agenceIds) {
    }

    record ClientCourant(String code, String statut, Long agenceId, String agenceLibelle, Long agentId) {
    }
}
