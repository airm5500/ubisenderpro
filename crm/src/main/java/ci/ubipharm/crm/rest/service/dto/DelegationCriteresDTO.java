package ci.ubipharm.crm.rest.service.dto;

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
public class DelegationCriteresDTO {

    /** Uniquement les delegations actives aujourd'hui. */
    private boolean enCours;
    /** Agent titulaire ou remplacant. */
    private Long agentId;
}
