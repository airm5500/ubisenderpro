package ci.ubipharm.crm.rest;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.AssistantImportService;
import ci.ubipharm.crm.rest.service.dto.ApercuImportDTO;
import ci.ubipharm.crm.rest.service.dto.ChampImportDTO;
import ci.ubipharm.crm.rest.service.dto.CorrectionLigneDTO;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.ImportDTO;
import ci.ubipharm.crm.rest.service.dto.ModeleImportDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Etapes de l'assistant d'import, communes a tous les types (clients, factures...). */
@RestController
@RequestMapping("/api/imports")
@PreAuthorize("hasAnyAuthority('DROIT_IMPORTS', 'DROIT_PRODUITS_GERER', 'DROIT_PROMOTIONS_GERER')")
@RequiredArgsConstructor
public class AssistantImportRessource {

    private final AssistantImportService assistantImportService;

    @GetMapping("/{type:[a-z]+}/champs")
    public List<ChampImportDTO> champs(@PathVariable String type) {
        return assistantImportService.champs(type);
    }

    @GetMapping("/{type:[a-z]+}/exemplaire")
    public ResponseEntity<byte[]> exemplaire(@PathVariable String type) {
        return telechargement(assistantImportService.exemplaire(type));
    }

    @PostMapping(value = "/{type:[a-z]+}/apercu", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApercuImportDTO apercu(@PathVariable String type, @RequestPart("fichier") MultipartFile fichier,
            @RequestParam(required = false) Long modeleId) {
        return assistantImportService.apercu(type, fichier, modeleId);
    }

    @GetMapping("/{type:[a-z]+}/modeles")
    public List<ModeleImportDTO> modeles(@PathVariable String type) {
        return assistantImportService.modeles(type);
    }

    @PostMapping("/{type:[a-z]+}/modeles")
    public ModeleImportDTO enregistrerModele(@PathVariable String type, @Valid @RequestBody ModeleImportDTO modele) {
        return assistantImportService.enregistrerModele(type, modele);
    }

    @PostMapping("/modeles/{id:\\d+}/archivage")
    public ResponseEntity<Void> archiverModele(@PathVariable Long id) {
        assistantImportService.archiverModele(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id:\\d+}/lignes/{ligneId:\\d+}")
    public ImportDTO corriger(@PathVariable Long id, @PathVariable Long ligneId, @Valid @RequestBody CorrectionLigneDTO correction) {
        return assistantImportService.corriger(id, ligneId, correction);
    }

    @GetMapping("/{id:\\d+}/rejets")
    public ResponseEntity<byte[]> rejets(@PathVariable Long id) {
        return telechargement(assistantImportService.rejets(id));
    }

    private ResponseEntity<byte[]> telechargement(FichierDTO fichier) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fichier.getNom()).build().toString())
                .contentType(MediaType.parseMediaType(fichier.getTypeContenu()))
                .body(fichier.getContenu());
    }
}
