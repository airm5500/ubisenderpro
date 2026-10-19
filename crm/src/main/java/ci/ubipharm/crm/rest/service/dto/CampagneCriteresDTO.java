package ci.ubipharm.crm.rest.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Filtres de la liste des campagnes. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampagneCriteresDTO {

    private String q;
    private String statut;
    private String typeCampagne;

    @Min(value = 0, message = "La page doit être positive.")
    @Builder.Default
    private int page = 0;

    @Min(value = 1, message = "La taille doit être au moins 1.")
    @Max(value = 200, message = "La taille ne peut pas dépasser 200.")
    @Builder.Default
    private int taille = 25;
}
