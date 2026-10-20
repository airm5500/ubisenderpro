package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Synthese d'un ecran, d'un appel ou d'un type de mesure sur la periode : moyenne, pire duree et part des lenteurs. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportExperienceLigneDTO {

    private String cle;
    private long nombre;
    private long moyenneMs;
    private long maxMs;
    /** Part (%) des mesures d'au moins 1 s, puis d'au moins 3 s. */
    private double part1s;
    private double part3s;
    private long echecs;
}
