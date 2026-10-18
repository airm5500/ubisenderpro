package ci.ubipharm.crm.sms.rest;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.sms.rest.service.FournisseurSmsService;
import ci.ubipharm.crm.sms.rest.service.dto.EssaiSmsDTO;
import ci.ubipharm.crm.sms.rest.service.dto.FournisseurSmsDTO;
import ci.ubipharm.crm.sms.rest.service.dto.FournisseurSmsDemandeDTO;
import ci.ubipharm.crm.sms.rest.service.dto.ResultatAppelSmsDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sms/fournisseurs")
@PreAuthorize("hasAuthority('DROIT_FOURNISSEURS_SMS')")
@RequiredArgsConstructor
public class FournisseurSmsRessource {

    private final FournisseurSmsService fournisseurSmsService;

    @GetMapping
    public List<FournisseurSmsDTO> lister() {
        return fournisseurSmsService.lister();
    }

    @GetMapping("/{id}")
    public FournisseurSmsDTO consulter(@PathVariable Long id) {
        return fournisseurSmsService.consulter(id);
    }

    @PostMapping
    public FournisseurSmsDTO creer(@Valid @RequestBody FournisseurSmsDemandeDTO demande) {
        return fournisseurSmsService.creer(demande);
    }

    @PutMapping("/{id}")
    public FournisseurSmsDTO modifier(@PathVariable Long id, @Valid @RequestBody FournisseurSmsDemandeDTO demande) {
        return fournisseurSmsService.modifier(id, demande);
    }

    @PostMapping("/{id}/defaut")
    public FournisseurSmsDTO definirParDefaut(@PathVariable Long id) {
        return fournisseurSmsService.definirParDefaut(id);
    }

    @PostMapping("/{id}/essai")
    public ResultatAppelSmsDTO essayer(@PathVariable Long id, @Valid @RequestBody EssaiSmsDTO essai) {
        return fournisseurSmsService.essayer(id, essai);
    }

    @GetMapping("/{id}/solde")
    public ResultatAppelSmsDTO solde(@PathVariable Long id) {
        return fournisseurSmsService.solde(id);
    }
}
