package ci.ubipharm.crm.rest.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReferentielDemandeDTO {

    @NotBlank(message = "Choisissez le type de référentiel.")
    private String type;

    @Size(max = 30, message = "Le code tient en 30 caractères.")
    private String code;

    @NotBlank(message = "Saisissez le libellé.")
    @Size(max = 100, message = "Le libellé tient en 100 caractères.")
    private String libelle;

    private Long parentId;

    private Long agenceId;
}
