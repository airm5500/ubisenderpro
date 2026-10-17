package ci.ubipharm.crm.recouvrement.rest.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Criteres combinables de selection des factures d'une campagne (cahier des charges 4.3). */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CriteresCampagneDTO {

    private List<Long> agenceIds;
    private List<Long> agentIds;
    private List<Long> clientIds;
    private List<Long> sousTypeIds;
    private List<String> segments;
    private BigDecimal soldeMin;
    private BigDecimal soldeMax;
    private LocalDate echeanceDu;
    private LocalDate echeanceAu;
    /** Relance avant echeance : de J-joursAvantMax a J-joursAvantMin. */
    private Integer joursAvantMin;
    private Integer joursAvantMax;
    /** Relance apres echeance : entre joursRetardMin et joursRetardMax jours de retard. */
    private Integer joursRetardMin;
    private Integer joursRetardMax;
}
