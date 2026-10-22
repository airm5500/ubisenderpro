package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.FactureCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.FactureDTO;
import ci.ubipharm.crm.rest.service.dto.FactureDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.SousTypeDTO;
import ci.ubipharm.crm.rest.service.dto.SuiviFactureDTO;
import ci.ubipharm.crm.rest.service.dto.SuiviFactureDemandeDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;

public interface FactureService {

    PageDTO<FactureDTO> rechercher(FactureCriteresDTO criteres);

    List<SousTypeDTO> sousTypes();

    FactureDTO consulter(Long id);

    /** Facture arrivee entre deux imports, pour un client du perimetre. */
    FactureDTO creer(FactureDemandeDTO demande);

    /** Correction ou reglement : un solde a zero solde la facture, comme a l'import. */
    FactureDTO modifier(Long id, FactureDemandeDTO demande);

    /** Promesse de paiement, litige, contentieux, ou levee : suspend ou reprend les relances de la facture. */
    FactureDTO poserSuivi(Long id, SuiviFactureDemandeDTO demande);

    List<SuiviFactureDTO> historiqueSuivi(Long id);
}
