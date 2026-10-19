package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un role sur un module ; moduleCode vide = tous les modules. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HabilitationDTO {

    @jakarta.validation.constraints.NotBlank(message = "Choisissez le rôle.")
    private String roleCode;
    private String roleLibelle;
    private String moduleCode;
    private String moduleLibelle;
}
