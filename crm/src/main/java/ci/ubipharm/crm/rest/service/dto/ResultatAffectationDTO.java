package ci.ubipharm.crm.rest.service.dto;

import java.util.ArrayList;
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
public class ResultatAffectationDTO {

    private int nbTraites;
    private int nbInchanges;
    /** Un motif par client refuse. */
    @Builder.Default
    private List<String> refus = new ArrayList<>();
}
