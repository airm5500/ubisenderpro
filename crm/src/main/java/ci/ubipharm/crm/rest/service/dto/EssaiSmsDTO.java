package ci.ubipharm.crm.rest.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** SMS d'essai envoye depuis l'ecran d'administration. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EssaiSmsDTO {

    @NotBlank(message = "Saisissez le numéro qui recevra l'essai.")
    private String numero;

    @NotBlank(message = "Saisissez le texte de l'essai.")
    @Size(max = 480, message = "Un SMS d'essai tient en 480 caractères.")
    private String message;
}
