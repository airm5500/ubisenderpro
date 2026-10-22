package ci.ubipharm.crm.rest.service.dto;

import java.util.List;

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
public class TableurDTO {

    /** En-tetes normalises (cles des valeurs de chaque ligne). */
    private List<String> entetes;
    /** En-tetes tels qu'ils figurent dans le fichier, dans le meme ordre. */
    private List<String> entetesOrigine;
    private List<LigneTableurDTO> lignes;
}
