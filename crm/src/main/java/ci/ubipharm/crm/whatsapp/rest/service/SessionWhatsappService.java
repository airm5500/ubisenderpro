package ci.ubipharm.crm.whatsapp.rest.service;

import java.util.List;

import ci.ubipharm.crm.whatsapp.rest.service.dto.EtatSessionDTO;
import ci.ubipharm.crm.whatsapp.rest.service.dto.SessionAgentDTO;

/** Liaison du telephone de chaque agent : la direction gere tous les numeros, un agent seulement le sien. */
public interface SessionWhatsappService {

    List<SessionAgentDTO> sessions();

    /** Demarre la session du numero de l'agent ; renvoie le QR a scanner avec son telephone. */
    EtatSessionDTO connecter(Long agentId);

    EtatSessionDTO etat(Long agentId);

    void deconnecter(Long agentId);
}
