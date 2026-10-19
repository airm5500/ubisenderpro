package ci.ubipharm.crm.rest;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.JournalService;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.JournalCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.JournalLigneDTO;
import ci.ubipharm.crm.rest.service.dto.JournalReferentielDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Journal des actions de toute l'application, en lecture seule. */
@RestController
@RequestMapping("/api/journal")
@PreAuthorize("hasAuthority('DROIT_JOURNAL')")
@RequiredArgsConstructor
public class JournalRessource {

    private final JournalService journalService;

    @GetMapping
    public PageDTO<JournalLigneDTO> rechercher(@Valid JournalCriteresDTO criteres) {
        return journalService.rechercher(criteres);
    }

    @GetMapping("/referentiel")
    public JournalReferentielDTO referentiel() {
        return journalService.referentiel();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exporter(@Valid JournalCriteresDTO criteres) {
        return telechargement(journalService.exporter(criteres));
    }

    @GetMapping("/export-excel")
    public ResponseEntity<byte[]> exporterExcel(@Valid JournalCriteresDTO criteres) {
        return telechargement(journalService.exporterExcel(criteres));
    }

    private static ResponseEntity<byte[]> telechargement(FichierDTO fichier) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fichier.getNom()).build().toString())
                .contentType(MediaType.parseMediaType(fichier.getTypeContenu()))
                .body(fichier.getContenu());
    }
}
