package ci.ubipharm.crm.rest;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.SupportDiagnosticService;
import ci.ubipharm.crm.rest.service.SupportSanteService;
import ci.ubipharm.crm.rest.service.dto.SupportErreurDTO;
import ci.ubipharm.crm.rest.service.dto.SupportErreurNavigateurDTO;
import ci.ubipharm.crm.rest.service.dto.SupportSondeDTO;
import lombok.RequiredArgsConstructor;

/** Diagnostic (erreurs capturees) et sante de l'application, reserves au droit Support ; tout utilisateur connecte remonte ses erreurs. */
@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportDiagnosticRessource {

    private static final String SUPPORT = "hasAuthority('DROIT_SUPPORT')";

    private final SupportDiagnosticService supportDiagnosticService;
    private final SupportSanteService supportSanteService;

    @GetMapping("/erreurs")
    @PreAuthorize(SUPPORT)
    public List<SupportErreurDTO> erreurs(@RequestParam(defaultValue = "false") boolean traitees) {
        return supportDiagnosticService.lister(traitees);
    }

    @PostMapping("/erreurs/{id}/traitement")
    @PreAuthorize(SUPPORT)
    public SupportErreurDTO traiter(@PathVariable Long id) {
        return supportDiagnosticService.traiter(id);
    }

    @PostMapping("/erreurs/navigateur")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void erreurNavigateur(@RequestBody SupportErreurNavigateurDTO erreur) {
        supportDiagnosticService.capturerNavigateur(erreur);
    }

    @GetMapping("/sante")
    @PreAuthorize(SUPPORT)
    public List<SupportSondeDTO> sante() {
        return supportSanteService.sonder();
    }
}
