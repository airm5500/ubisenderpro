package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Factures retenues par les criteres mais qui ne seront pas relancees, regroupees par motif. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MotifExclusionDTO {

    private String motif;
    private long nb;
}
