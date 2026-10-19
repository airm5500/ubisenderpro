package ci.ubipharm.crm.rest.service;

import java.util.List;
import java.util.Map;

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

    /** Rupture de stock ou remise en stock (datee : le produit devient « de retour »). */
    ProduitDTO changerDisponibilite(Long id, boolean disponible);

    /** Les produits annonces doivent etre actifs et disponibles. */
    void verifierAnnoncables(List<Long> ids);

    /** Valeur de {produits_disponibles} pour ces produits. */
    Map<String, String> valeursCampagne(List<Long> ids);
}
