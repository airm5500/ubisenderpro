package ci.ubipharm.crm.recouvrement.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Filtres de la liste des modeles. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ModeleMessageCriteresDTO {

    private String typeCampagne;
    private String statut;
    private String canal;
}
