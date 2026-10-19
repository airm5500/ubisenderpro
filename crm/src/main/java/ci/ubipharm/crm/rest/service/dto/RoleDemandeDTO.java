package ci.ubipharm.crm.rest.service.dto;

import java.util.List;

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
public class RoleDemandeDTO {

    @NotBlank(message = "Le nom du rôle est obligatoire.")
    @Size(max = 100, message = "Le nom du rôle tient en 100 caractères.")
    private String libelle;

    @Size(max = 500, message = "La description tient en 500 caractères.")
    private String description;

    private boolean visionGlobale;

    /** Codes des droits coches. */
    private List<String> droits;
}
