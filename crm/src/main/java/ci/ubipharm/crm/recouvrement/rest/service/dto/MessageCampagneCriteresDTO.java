package ci.ubipharm.crm.recouvrement.rest.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Filtres des messages d'une campagne. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageCampagneCriteresDTO {

    private String statut;
    private Long agentId;
    private String q;

    @Min(value = 0, message = "La page doit etre positive.")
    @Builder.Default
    private int page = 0;

    @Min(value = 1, message = "La taille doit etre au moins 1.")
    @Max(value = 200, message = "La taille ne peut pas depasser 200.")
    @Builder.Default
    private int taille = 25;
}
