package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Region, ville, commune et tournee d'un client, coherentes entre elles. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LocalisationDTO {

    private Long regionId;
    private String region;
    private Long villeId;
    private String ville;
    private Long communeId;
    private String commune;
    private Long tourneeId;
    private String tournee;
}
