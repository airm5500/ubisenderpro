package ci.ubipharm.crm.rest.service.dto;

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
public class SupportMessageDemandeDTO {

    @NotBlank(message = "Saisissez votre message.")
    @Size(max = 10000, message = "Le message tient en 10 000 caractères.")
    private String contenu;
}
