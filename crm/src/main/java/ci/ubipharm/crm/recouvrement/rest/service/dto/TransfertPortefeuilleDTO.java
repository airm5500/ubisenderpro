package ci.ubipharm.crm.recouvrement.rest.service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Transfert de tout le portefeuille d'un agent (depart, changement de poste). */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransfertPortefeuilleDTO {

    @NotNull(message = "Choisissez l'agent qui reprend le portefeuille.")
    private Long versAgentId;

    @Size(max = 255, message = "Le motif ne peut pas depasser 255 caracteres.")
    private String motif;
}
