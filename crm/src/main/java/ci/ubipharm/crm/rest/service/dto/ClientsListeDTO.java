package ci.ubipharm.crm.rest.service.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
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
public class ClientsListeDTO {

    @NotEmpty(message = "Choisissez au moins un client.")
    private List<Long> clientIds;

    /** AJOUT : dans la liste quels que soient les criteres ; EXCLUSION : hors de la liste ; RETRAIT : annule l'un ou l'autre. */
    @Pattern(regexp = "AJOUT|EXCLUSION|RETRAIT", message = "Action inconnue.")
    private String sens;
}
