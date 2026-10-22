package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Periode pendant laquelle un client etait confie a un agent. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AffectationDTO {

    private Long id;
    private Long clientId;
    private Long agentId;
    private String agentNom;
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private String motifDebut;
    private String motifFin;
    private String affectePar;
    private String retirePar;
}
