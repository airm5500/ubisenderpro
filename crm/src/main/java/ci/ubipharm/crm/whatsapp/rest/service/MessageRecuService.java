package ci.ubipharm.crm.whatsapp.rest.service;

import ci.ubipharm.crm.socle.dto.PageDTO;
import ci.ubipharm.crm.whatsapp.rest.service.dto.MessageRecuCriteresDTO;
import ci.ubipharm.crm.whatsapp.rest.service.dto.MessageRecuDTO;

public interface MessageRecuService {

    /** Reponses des clients, dans le perimetre de l'utilisateur (un agent : ses numeros et ceux qu'il remplace). */
    PageDTO<MessageRecuDTO> rechercher(MessageRecuCriteresDTO criteres);
}
