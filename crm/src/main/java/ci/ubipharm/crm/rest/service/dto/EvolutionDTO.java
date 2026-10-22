package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Courbes d'evolution d'un tableau de bord : un point par jour, ou par semaine au-dela de 60 jours. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvolutionDTO {

    private LocalDate du;
    private LocalDate au;
    /** JOUR ou SEMAINE. */
    private String pas;
    private List<PointEvolutionDTO> points;
}
