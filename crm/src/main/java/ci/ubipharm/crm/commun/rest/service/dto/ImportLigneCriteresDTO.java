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
public class ImportLigneCriteresDTO {

    @Pattern(regexp = "CREATION|MISE_A_JOUR|IDENTIQUE|REJET", message = "Action inconnue.")
    private String action;

    @Min(value = 0, message = "La page doit être positive.")
    @Builder.Default
    private int page = 0;

    @Min(value = 1, message = "La taille doit être au moins 1.")
    @Max(value = 500, message = "La taille ne peut pas dépasser 500.")
    @Builder.Default
    private int taille = 100;
}
