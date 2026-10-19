package ci.ubipharm.crm.rest;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.HistoriqueEnvoiService;
import ci.ubipharm.crm.rest.service.dto.EnvoiHistoriqueCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiHistoriqueDTO;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Historique complet des envois, tous canaux et toutes origines. */
@RestController
@RequestMapping("/api/historique-envois")
@PreAuthorize("hasAuthority('DROIT_HISTORIQUE_ENVOIS')")
@RequiredArgsConstructor
public class HistoriqueEnvoiRessource {

    private final HistoriqueEnvoiService historiqueEnvoiService;

    @GetMapping
    public PageDTO<EnvoiHistoriqueDTO> rechercher(@Valid EnvoiHistoriqueCriteresDTO criteres) {
        return historiqueEnvoiService.rechercher(criteres);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exporter(@Valid EnvoiHistoriqueCriteresDTO criteres) {
        FichierDTO fichier = historiqueEnvoiService.exporter(criteres);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fichier.getNom()).build().toString())
                .contentType(MediaType.parseMediaType(fichier.getTypeContenu()))
                .body(fichier.getContenu());
    }
}
