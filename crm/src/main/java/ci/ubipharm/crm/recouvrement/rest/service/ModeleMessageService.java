package ci.ubipharm.crm.recouvrement.rest.service;

import java.util.List;

import ci.ubipharm.crm.recouvrement.rest.service.dto.ModeleMessageCriteresDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.ModeleMessageDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.ModeleMessageDemandeDTO;

public interface ModeleMessageService {

    List<ModeleMessageDTO> rechercher(ModeleMessageCriteresDTO criteres);

    ModeleMessageDTO consulter(Long id);

    /** Refuse un texte qui cite une variable inconnue. */
    ModeleMessageDTO creer(ModeleMessageDemandeDTO demande);

    /** Les messages deja figes des campagnes validees gardent l'ancien texte. */
    ModeleMessageDTO modifier(Long id, ModeleMessageDemandeDTO demande);

    ModeleMessageDTO archiver(Long id);
}
