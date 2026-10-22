package ci.ubipharm.crm.rest;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import ci.ubipharm.crm.rest.service.SupportExportService;
import ci.ubipharm.crm.rest.service.SupportSanteService;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.SupportErreurDTO;
import ci.ubipharm.crm.rest.service.dto.SupportErreurNavigateurDTO;
import ci.ubipharm.crm.rest.service.dto.SupportSondeDTO;
import ci.ubipharm.crm.rest.service.dto.SupportTicketDTO;
import lombok.RequiredArgsConstructor;

/** Diagnostic (erreurs capturees) et sante de l'application, reserves au droit Support ; tout utilisateur connecte remonte ses erreurs. */
@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportDiagnosticRessource {

    private static final String SUPPORT = "hasAuthority('DROIT_SUPPORT')";

    private final SupportDiagnosticService supportDiagnosticService;
    private final SupportSanteService supportSanteService;
    private final SupportExportService supportExportService;

    @GetMapping("/erreurs")
    @PreAuthorize(SUPPORT)
    public List<SupportErreurDTO> erreurs(@RequestParam(defaultValue = "false") boolean traitees, @RequestParam(required = false) String origine) {
        return supportDiagnosticService.lister(traitees, origine);
    }

    @GetMapping("/erreurs/{id}")
    @PreAuthorize(SUPPORT)
    public SupportErreurDTO erreur(@PathVariable Long id) {
        return supportDiagnosticService.consulter(id);
    }

    @PostMapping("/erreurs/{id}/ticket")
    @PreAuthorize(SUPPORT)
    public SupportTicketDTO creerTicket(@PathVariable Long id) {
        return supportDiagnosticService.creerTicket(id);
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

    @GetMapping("/diagnostic/export")
    @PreAuthorize(SUPPORT)
    public ResponseEntity<byte[]> exporter() {
        FichierDTO fichier = supportExportService.exporter();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fichier.getNom()).build().toString())
                .contentType(MediaType.parseMediaType(fichier.getTypeContenu()))
                .body(fichier.getContenu());
    }

    @GetMapping("/sante")
    @PreAuthorize(SUPPORT)
    public List<SupportSondeDTO> sante() {
        return supportSanteService.sonder();
    }
}
