package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.DiscussionCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.DiscussionDTO;
import ci.ubipharm.crm.rest.service.dto.MessageDiscussionDTO;
import ci.ubipharm.crm.rest.service.dto.ReponseDiscussionDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;

/**
 * Discussions WhatsApp : pour un numero de l'entreprise et un numero de client, les messages partis (relances, promotions,
 * envois en masse, reponses ecrites ici) et ceux du client, dans l'ordre. Meme perimetre que les reponses des clients.
 */
public interface DiscussionService {

    PageDTO<DiscussionDTO> rechercher(DiscussionCriteresDTO criteres);

    /** Les 200 derniers messages, du plus ancien au plus recent. */
    List<MessageDiscussionDTO> fil(String numeroAgent, String numeroClient);

    /**
     * Envoie aussitot le message depuis le numero de la discussion : le sien ou celui d'un agent remplace. Refuse si le
     * parametre discussions.reponse_active est a NON, ou, pour un numero passe a l'API Meta, hors des 24 h qui suivent un
     * message du client.
     */
    MessageDiscussionDTO repondre(ReponseDiscussionDTO reponse);
}
