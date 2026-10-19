package ci.ubipharm.crm.rest.service.dto;

import java.util.List;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Numeros saisis ou colles, ou clients dont on verifie les numeros actifs. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VerificationNumerosDemandeDTO {

    @Size(max = 500, message = "500 numéros au plus par vérification.")
    private List<String> numeros;

    @Size(max = 500, message = "500 clients au plus par vérification.")
    private List<Long> clientIds;
}
