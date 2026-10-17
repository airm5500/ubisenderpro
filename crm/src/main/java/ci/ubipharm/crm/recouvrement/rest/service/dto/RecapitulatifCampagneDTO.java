package ci.ubipharm.crm.recouvrement.rest.service.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Recapitulatif affiche avant validation (cahier des charges 4.4). */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecapitulatifCampagneDTO {

    private long nbClients;
    private long nbFactures;
    private long nbMessages;
    private long nbExclus;
    private BigDecimal totalSolde;
    private List<AgentCampagneDTO> agents;
    private List<MotifExclusionDTO> exclusions;
    private List<MessageCampagneDTO> exemples;
}
