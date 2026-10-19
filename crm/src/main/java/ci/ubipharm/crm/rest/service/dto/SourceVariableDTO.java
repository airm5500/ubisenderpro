package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Champ de la base qu'une variable peut restituer. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SourceVariableDTO {

    private String source;
    private String libelle;
}
