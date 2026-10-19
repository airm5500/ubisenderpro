package ci.ubipharm.crm.rest.service.dto;

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
public class PromotionCriteresDTO {

    @Size(max = 100, message = "La recherche est trop longue.")
    private String q;

    /** EN_COURS (brouillons et actives), un statut, ou TOUS. */
    @Pattern(regexp = "EN_COURS|BROUILLON|ACTIVE|TERMINEE|ARCHIVEE|TOUS", message = "Statut inconnu.")
    @Builder.Default
    private String statut = "EN_COURS";
}
