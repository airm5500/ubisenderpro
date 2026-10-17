package ci.ubipharm.crm.recouvrement.rest;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.recouvrement.rest.service.AffectationService;
import ci.ubipharm.crm.recouvrement.rest.service.dto.AffectationDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.AffectationDemandeDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.ResultatAffectationDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.RetraitAffectationDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.TransfertPortefeuilleDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/recouvrement")
@PreAuthorize("hasAuthority('MODULE_RECOUVREMENT') and hasAnyRole('ADMIN', 'DIR_RECOUVREMENT', 'DIR_AGENCE')")
@RequiredArgsConstructor
public class AffectationRessource {

    private final AffectationService affectationService;

    @PostMapping("/affectations")
    public ResultatAffectationDTO affecter(@Valid @RequestBody AffectationDemandeDTO demande) {
        return affectationService.affecter(demande);
    }

    @PostMapping("/affectations/retrait")
    public ResultatAffectationDTO retirer(@Valid @RequestBody RetraitAffectationDTO retrait) {
        return affectationService.retirer(retrait);
    }

    @PostMapping("/agents/{agentId}/transfert")
    public ResultatAffectationDTO transferer(@PathVariable Long agentId, @Valid @RequestBody TransfertPortefeuilleDTO transfert) {
        return affectationService.transferer(agentId, transfert);
    }

    @GetMapping("/clients/{clientId}/affectations")
    public List<AffectationDTO> historique(@PathVariable Long clientId) {
        return affectationService.historique(clientId);
    }
}
