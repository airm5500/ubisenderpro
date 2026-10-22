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
public class FiltresCatalogueDTO {

    /** Valeurs deja saisies, pour les filtres et la saisie assistee. */
    private List<String> categories;
    private List<String> laboratoires;
}
