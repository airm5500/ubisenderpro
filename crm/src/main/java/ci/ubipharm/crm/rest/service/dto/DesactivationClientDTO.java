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
public class DesactivationClientDTO {

    @NotBlank(message = "Indiquez le motif de la désactivation.")
    @Size(max = 255, message = "Le motif tient en 255 caractères.")
    private String motif;
}
