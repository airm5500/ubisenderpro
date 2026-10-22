package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.AffectationDTO;
import ci.ubipharm.crm.rest.service.dto.AffectationDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.ResultatAffectationDTO;
import ci.ubipharm.crm.rest.service.dto.RetraitAffectationDTO;
import ci.ubipharm.crm.rest.service.dto.TransfertPortefeuilleDTO;

public interface AffectationService {

    /** Confie les clients a l'agent ; l'affectation precedente est close et reste dans l'historique. */
    ResultatAffectationDTO affecter(AffectationDemandeDTO demande);

    ResultatAffectationDTO retirer(RetraitAffectationDTO retrait);

    /** Reaffecte tout le portefeuille d'un agent a un autre. */
    ResultatAffectationDTO transferer(Long agentId, TransfertPortefeuilleDTO transfert);

    List<AffectationDTO> historique(Long clientId);
}
