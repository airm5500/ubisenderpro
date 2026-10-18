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
public class ChangementMotDePasseDTO {

    @NotBlank(message = "Saisissez votre mot de passe actuel.")
    private String actuel;

    @NotBlank(message = "Saisissez le nouveau mot de passe.")
    @Size(max = 100, message = "Le mot de passe est trop long.")
    private String nouveau;
}
