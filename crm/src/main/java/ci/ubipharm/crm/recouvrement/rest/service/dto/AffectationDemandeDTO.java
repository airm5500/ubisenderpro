package ci.ubipharm.crm.recouvrement.rest.service.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Confier des clients a un agent ; un client deja confie a un autre agent lui est reaffecte. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AffectationDemandeDTO {

    @NotEmpty(message = "Choisissez au moins un client.")
    @Size(max = 5000, message = "5 000 clients au plus par affectation.")
    private List<Long> clientIds;

    @NotNull(message = "Choisissez l'agent.")
    private Long agentId;

    @Size(max = 255, message = "Le motif ne peut pas depasser 255 caracteres.")
    private String motif;
}
