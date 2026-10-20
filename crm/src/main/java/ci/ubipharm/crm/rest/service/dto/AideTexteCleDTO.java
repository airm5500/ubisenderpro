package ci.ubipharm.crm.rest.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Texte a remettre a son origine. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AideTexteCleDTO {

    @NotBlank(message = "Précisez le type de texte.")
    @Pattern(regexp = "BOUTON|PAGE", message = "Le type de texte est BOUTON ou PAGE.")
    private String type;

    @NotBlank(message = "Précisez le texte à rétablir.")
    private String cle;
}
