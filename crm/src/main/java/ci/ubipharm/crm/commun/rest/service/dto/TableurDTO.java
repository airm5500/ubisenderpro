package ci.ubipharm.crm.commun.rest.service.dto;

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

    private List<String> entetes;
    private List<LigneTableurDTO> lignes;
}
