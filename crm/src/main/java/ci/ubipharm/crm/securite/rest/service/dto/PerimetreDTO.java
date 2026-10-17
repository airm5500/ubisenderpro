package ci.ubipharm.crm.securite.rest.service.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ce que l'utilisateur connecte a le droit de voir : toutes les agences
 * (vision globale) ou seulement celles auxquelles il est rattache aujourd'hui.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PerimetreDTO {

    private Long utilisateurId;
    private boolean visionGlobale;
    private List<Long> agenceIds;
}
