package ci.ubipharm.crm.rest.service.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Valeurs corrigees d'une ligne de simulation, par champ de l'application. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CorrectionLigneDTO {

    @NotNull(message = "Aucune valeur corrigée.")
    private Map<String, String> valeurs;
}
