package ci.ubipharm.crm.commun.rest.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Criteres de recherche, remplis par Spring a partir des query params. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientCriteresDTO {

    @Size(max = 100, message = "La recherche est trop longue.")
    private String q;

    private Long agenceId;

    @Size(max = 50, message = "La segmentation est trop longue.")
    private String segmentation;

    @Pattern(regexp = "ACTIF|ARCHIVE", message = "Statut inconnu.")
    @Builder.Default
    private String statut = "ACTIF";

    @Min(value = 0, message = "La page doit etre positive.")
    @Builder.Default
    private int page = 0;

    @Min(value = 1, message = "La taille doit etre au moins 1.")
    @Max(value = 100, message = "La taille ne peut pas depasser 100.")
    @Builder.Default
    private int taille = 25;
}
