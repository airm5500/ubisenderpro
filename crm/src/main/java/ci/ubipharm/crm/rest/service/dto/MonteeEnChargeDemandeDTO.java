package ci.ubipharm.crm.rest.service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Montee en charge d'un numero recent : activee ou coupee. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MonteeEnChargeDemandeDTO {

    @NotNull(message = "Indiquez si la montée en charge est active.")
    private Boolean active;
}
