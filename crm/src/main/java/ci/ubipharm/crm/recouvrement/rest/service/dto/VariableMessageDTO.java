package ci.ubipharm.crm.recouvrement.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Variable inserable dans un modele sous la forme {code}. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VariableMessageDTO {

    private Long id;
    private String code;
    private String libelle;
    private String source;
    private String exemple;
    private String statut;
}
