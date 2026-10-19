package ci.ubipharm.crm.rest.service;

import java.util.List;
import java.util.Map;

import ci.ubipharm.crm.rest.service.dto.SourceVariableDTO;
import ci.ubipharm.crm.rest.service.dto.VariableMessageDTO;
import ci.ubipharm.crm.rest.service.dto.VariableMessageDemandeDTO;

public interface VariableMessageService {

    /** Variables actives, a inserer dans les modeles sous la forme {code}. */
    List<VariableMessageDTO> variables();

    /** Champs de la base qu'une variable peut restituer, colonnes ajoutees depuis comprises. */
    List<SourceVariableDTO> sources();

    VariableMessageDTO creer(VariableMessageDemandeDTO demande);

    VariableMessageDTO modifier(Long id, VariableMessageDemandeDTO demande);

    /** Refuse si un modele actif utilise encore la variable. */
    VariableMessageDTO archiver(Long id);

    /** Code -> source des variables actives. */
    Map<String, String> sourcesParCode();
}
