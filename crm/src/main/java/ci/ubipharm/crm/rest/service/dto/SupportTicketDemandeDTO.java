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
public class SupportTicketDemandeDTO {

    @NotBlank(message = "Saisissez le sujet du ticket.")
    @Size(max = 200, message = "Le sujet tient en 200 caractères.")
    private String sujet;

    @NotBlank(message = "Choisissez la catégorie du ticket.")
    private String categorie;

    /** NORMALE si vide. */
    private String priorite;

    @NotBlank(message = "Décrivez votre demande.")
    @Size(max = 10000, message = "Le message tient en 10 000 caractères.")
    private String message;
}
