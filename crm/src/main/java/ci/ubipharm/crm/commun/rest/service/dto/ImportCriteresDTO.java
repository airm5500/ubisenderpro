package ci.ubipharm.crm.commun.rest.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
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
public class ImportCriteresDTO {

    @Pattern(regexp = "CLIENTS|FACTURES", message = "Type d'import inconnu.")
    private String type;

    @Min(value = 0, message = "La page doit être positive.")
    @Builder.Default
    private int page = 0;

    @Min(value = 1, message = "La taille doit être au moins 1.")
    @Max(value = 100, message = "La taille ne peut pas dépasser 100.")
    @Builder.Default
    private int taille = 25;
}
