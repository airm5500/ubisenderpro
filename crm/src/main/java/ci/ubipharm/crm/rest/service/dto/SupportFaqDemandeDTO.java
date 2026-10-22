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
public class SupportFaqDemandeDTO {

    @NotBlank(message = "Saisissez la catégorie.")
    @Size(max = 100, message = "La catégorie tient en 100 caractères.")
    private String categorie;

    @NotBlank(message = "Saisissez la question.")
    @Size(max = 300, message = "La question tient en 300 caractères.")
    private String question;

    @NotBlank(message = "Saisissez la réponse.")
    @Size(max = 10000, message = "La réponse tient en 10 000 caractères.")
    private String reponse;

    /** A la suite des autres si vide. */
    private Integer ordre;
}
