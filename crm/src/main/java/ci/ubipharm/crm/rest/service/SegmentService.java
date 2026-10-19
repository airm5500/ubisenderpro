package ci.ubipharm.crm.rest.service;

import java.util.List;
import java.util.Set;

import ci.ubipharm.crm.rest.service.dto.SegmentDTO;
import ci.ubipharm.crm.rest.service.dto.SegmentDemandeDTO;

public interface SegmentService {

    /** Badges proposes a la creation d'un segment. */
    Set<String> BADGES = Set.of("COURONNE", "TROPHEE", "ETOILE", "MEDAILLE", "BOUCLIER", "ECLAIR", "COEUR", "CADEAU", "ETINCELLES",
            "DRAPEAU", "SIGNET", "SOLEIL");

    List<SegmentDTO> lister(boolean archives);

    SegmentDTO creer(SegmentDemandeDTO demande);

    SegmentDTO modifier(Long id, SegmentDemandeDTO demande);

    SegmentDTO archiver(Long id);

    SegmentDTO reactiver(Long id);
}
