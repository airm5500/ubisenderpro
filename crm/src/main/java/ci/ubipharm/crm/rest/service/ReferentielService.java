package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.ReferentielDTO;
import ci.ubipharm.crm.rest.service.dto.ReferentielDemandeDTO;

public interface ReferentielService {

    /** Entrees d'un type, actives ou archivees ; toutes si le type est vide. */
    List<ReferentielDTO> lister(String type, boolean archives);

    /** Toutes les entrees, archivees comprises : base des controles des fiches clients et des imports. */
    ResolutionReferentiels resolution();

    ReferentielDTO creer(ReferentielDemandeDTO demande);

    ReferentielDTO modifier(Long id, ReferentielDemandeDTO demande);

    /** Une entree archivee reste sur les clients qui la portent mais ne se choisit plus. */
    ReferentielDTO archiver(Long id);

    ReferentielDTO reactiver(Long id);
}
