package ci.ubipharm.crm.rest.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Motif d'un rejet ou d'une annulation. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MotifDTO {

    @NotBlank(message = "Saisissez le motif.")
    @Size(max = 500, message = "Le motif ne peut pas dépasser 500 caractères.")
    private String motif;
}
