package ci.ubipharm.crm.rest.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Vide : le jeton ou le secret deja enregistre est conserve. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompteMetaDemandeDTO {

    @Size(max = 1000, message = "Le jeton d'accès ne peut pas dépasser 1000 caractères.")
    private String jetonAcces;

    @Size(max = 200, message = "Le secret de l'application ne peut pas dépasser 200 caractères.")
    private String secretApplication;

    @NotBlank(message = "Saisissez la version de l'API (ex. v21.0).")
    @Pattern(regexp = "v\\d{1,3}\\.\\d{1,2}", message = "La version de l'API s'écrit comme v21.0.")
    private String versionApi;
}
