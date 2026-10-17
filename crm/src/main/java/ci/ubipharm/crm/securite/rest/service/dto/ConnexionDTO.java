package ci.ubipharm.crm.securite.rest.service.dto;

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
public class ConnexionDTO {

    @NotBlank(message = "L'identifiant est obligatoire.")
    @Size(max = 100, message = "L'identifiant est trop long.")
    private String login;

    @NotBlank(message = "Le mot de passe est obligatoire.")
    @Size(max = 200, message = "Le mot de passe est trop long.")
    private String motDePasse;
}
