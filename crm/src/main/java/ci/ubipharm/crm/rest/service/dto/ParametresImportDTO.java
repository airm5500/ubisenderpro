package ci.ubipharm.crm.rest.service.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Choix faits dans l'assistant avant la simulation ; sans choix, la correspondance est proposee automatiquement. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ParametresImportDTO {

    /** Champ de l'application -> cle de colonne du fichier. */
    private Map<String, String> correspondance;
    private Long modeleId;
}
