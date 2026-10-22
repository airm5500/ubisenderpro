package ci.ubipharm.crm.rest.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class SegmentDemandeDTO {

    /** Code lu dans les fichiers importes ; ne change plus apres la creation. */
    @Pattern(regexp = "[A-Za-z0-9_-]{1,20}", message = "Le code du segment tient en 1 à 20 lettres, chiffres, - ou _.")
    private String code;

    @NotBlank(message = "Saisissez le nom du segment.")
    @Size(max = 100, message = "Le nom tient en 100 caractères.")
    private String libelle;

    @NotBlank(message = "Choisissez un badge.")
    private String badge;

    private Integer ordre;
}
