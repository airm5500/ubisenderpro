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
public class AideTexteDemandeDTO {

    @NotBlank(message = "Précisez le type de texte.")
    @Pattern(regexp = "BOUTON|PAGE", message = "Le type de texte est BOUTON ou PAGE.")
    private String type;

    @NotBlank(message = "Précisez le texte à modifier.")
    @Size(max = 200, message = "La clé tient en 200 caractères.")
    private String cle;

    @NotBlank(message = "Saisissez le titre.")
    @Size(max = 150, message = "Le titre tient en 150 caractères.")
    private String titre;

    @NotBlank(message = "Saisissez le texte d'aide.")
    @Size(max = 4000, message = "Le texte d'aide tient en 4 000 caractères.")
    private String texte;
}
