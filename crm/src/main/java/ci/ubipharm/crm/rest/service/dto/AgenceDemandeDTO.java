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
public class AgenceDemandeDTO {

    @NotBlank(message = "Saisissez le code de l'agence.")
    @Pattern(regexp = "[A-Za-z0-9_-]{2,30}", message = "Le code de l'agence tient en 2 à 30 lettres, chiffres, - ou _.")
    private String code;

    @Size(max = 30, message = "Le code d'import ne peut pas dépasser 30 caractères.")
    private String codeImport;

    @NotBlank(message = "Saisissez le nom de l'agence.")
    @Size(max = 100, message = "Le nom ne peut pas dépasser 100 caractères.")
    private String libelle;
}
