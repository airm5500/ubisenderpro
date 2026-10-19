package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.ModeleMessageCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.ModeleMessageDTO;
import ci.ubipharm.crm.rest.service.dto.ModeleMessageDemandeDTO;

/** Modeles de messages d'un module : RECOUVREMENT (relances) ou MARKETING (promotions). */
public interface ModeleMessageService {

    List<ModeleMessageDTO> rechercher(String module, ModeleMessageCriteresDTO criteres);

    ModeleMessageDTO consulter(String module, Long id);

    /** Refuse un texte qui cite une variable inconnue. */
    ModeleMessageDTO creer(String module, ModeleMessageDemandeDTO demande);

    /** Les messages deja figes des campagnes validees gardent l'ancien texte. */
    ModeleMessageDTO modifier(String module, Long id, ModeleMessageDemandeDTO demande);

    ModeleMessageDTO archiver(String module, Long id);
}
