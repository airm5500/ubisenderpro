package ci.ubipharm.crm.rest.service.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Une ligne de repartition : sous-type, tranche de retard, motif d'echec, statut... */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RepartitionDTO {

    private String code;
    private String libelle;
    private long nombre;
    private BigDecimal montant;
}
