package ci.ubipharm.crm.recouvrement.rest.service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un client vu depuis le portefeuille : son agent et ce qu'il reste a recouvrer. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PortefeuilleClientDTO {

    private Long clientId;
    private String code;
    private String raisonSociale;
    private String nomPharmacien;
    private Long agenceId;
    private String agenceLibelle;
    private String segmentation;
    private String numeroPrincipal;
    private Long agentId;
    private String agentNom;
    private LocalDateTime affecteLe;
    private int nbFacturesOuvertes;
    private BigDecimal totalSolde;
    private int retardMax;
}
