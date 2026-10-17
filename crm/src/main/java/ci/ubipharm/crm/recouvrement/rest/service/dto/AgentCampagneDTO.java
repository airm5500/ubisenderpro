package ci.ubipharm.crm.recouvrement.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Agent dont le numero envoie une partie de la campagne. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgentCampagneDTO {

    private Long agentId;
    private String agentNom;
    private String numeroExpediteur;
    private long nbClients;
    private long nbMessages;
}
