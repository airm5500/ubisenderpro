package ci.ubipharm.crm.recouvrement.rest.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Creation ou modification d'une variable : son code et le champ de la base qu'elle restitue. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VariableMessageDemandeDTO {

    @NotBlank(message = "Saisissez le code de la variable.")
    @Pattern(regexp = "[a-z][a-z0-9_]{1,49}", message = "Le code s'ecrit en minuscules, chiffres et _ (ex. nom_client).")
    private String code;

    @NotBlank(message = "Saisissez le libellé de la variable.")
    @Size(max = 100, message = "Le libellé ne peut pas dépasser 100 caractères.")
    private String libelle;

    @NotBlank(message = "Choisissez le champ restitué par la variable.")
    private String source;

    @Size(max = 100, message = "L'exemple ne peut pas dépasser 100 caractères.")
    private String exemple;
}
