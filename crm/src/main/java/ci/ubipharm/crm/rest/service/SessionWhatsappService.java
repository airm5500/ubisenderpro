package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.EtatSessionDTO;
import ci.ubipharm.crm.rest.service.dto.SessionAgentDTO;

/** Liaison du telephone de chaque agent : la direction gere tous les numeros, un agent seulement le sien. */
public interface SessionWhatsappService {

    List<SessionAgentDTO> sessions();

    /** Demarre la session du numero de l'agent ; renvoie le QR a scanner avec son telephone. */
    EtatSessionDTO connecter(Long agentId);

    EtatSessionDTO etat(Long agentId);

    void deconnecter(Long agentId);

    /** Fait passer le numero a l'API officielle de Meta, apres verification du numero chez Meta. */
    SessionAgentDTO passerMeta(Long agentId, String phoneNumberId);

    /** Ramene le numero a WhatsApp Web : il faudra de nouveau scanner le QR code. */
    SessionAgentDTO passerWhatsappWeb(Long agentId);
}
