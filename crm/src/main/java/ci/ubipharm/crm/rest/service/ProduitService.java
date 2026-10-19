package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.FiltresCatalogueDTO;
import ci.ubipharm.crm.rest.service.dto.ProduitCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.ProduitDTO;
import ci.ubipharm.crm.rest.service.dto.ProduitDemandeDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;

/** Catalogue des produits du Marketing : saisie a la main, import, archivage (jamais de suppression). */
public interface ProduitService {

    PageDTO<ProduitDTO> rechercher(ProduitCriteresDTO criteres);

    ProduitDTO consulter(Long id);

    FiltresCatalogueDTO filtres();

    ProduitDTO creer(ProduitDemandeDTO demande);

    ProduitDTO modifier(Long id, ProduitDemandeDTO demande);

    ProduitDTO archiver(Long id);

    ProduitDTO reactiver(Long id);
}
