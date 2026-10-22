package ci.ubipharm.crm.rest.service.dto;

import java.math.BigDecimal;

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
public class AgentDTO {

    private Long id;
    private String login;
    private String nomComplet;
    private String numeroWhatsapp;
    private String agences;
    private int nbClients;
    private BigDecimal totalSolde;
    /** Remplacant pendant une delegation en cours, sinon null. */
    private String remplacant;
}
