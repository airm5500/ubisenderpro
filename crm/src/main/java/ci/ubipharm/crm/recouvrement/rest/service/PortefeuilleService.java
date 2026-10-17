package ci.ubipharm.crm.recouvrement.rest.service;

import java.util.List;

import ci.ubipharm.crm.recouvrement.rest.service.dto.AgentDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.NumeroAgentDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.PortefeuilleClientDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.PortefeuilleCriteresDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;

public interface PortefeuilleService {

    PageDTO<PortefeuilleClientDTO> rechercher(PortefeuilleCriteresDTO criteres);

    List<AgentDTO> agents(Long agenceId);

    AgentDTO enregistrerNumero(Long agentId, NumeroAgentDTO numero);

    /**
     * Agents dont l'utilisateur connecte gere les clients : lui-meme et les agents absents qu'il remplace
     * aujourd'hui. Null pour un profil de direction, qui n'est pas limite a un portefeuille.
     */
    List<Long> agentsGeres();
}
