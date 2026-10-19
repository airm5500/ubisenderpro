package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.SupportFaqDTO;
import ci.ubipharm.crm.rest.service.dto.SupportFaqDemandeDTO;

/** FAQ du centre de support : lue par tous, tenue par les titulaires du droit Support ; archivee, jamais supprimee. */
public interface SupportFaqService {

    /** Questions actives ou archivees, par categorie puis dans l'ordre d'affichage. */
    List<SupportFaqDTO> lister(boolean archives);

    SupportFaqDTO creer(SupportFaqDemandeDTO demande);

    SupportFaqDTO modifier(Long id, SupportFaqDemandeDTO demande);

    SupportFaqDTO archiver(Long id);

    SupportFaqDTO reactiver(Long id);
}
