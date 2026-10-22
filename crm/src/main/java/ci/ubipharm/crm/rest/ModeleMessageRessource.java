package ci.ubipharm.crm.rest;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.ModeleMessageService;
import ci.ubipharm.crm.rest.service.dto.ModeleMessageCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.ModeleMessageDTO;
import ci.ubipharm.crm.rest.service.dto.ModeleMessageDemandeDTO;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import ci.ubipharm.crm.rest.service.ModeleWordService;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.LectureModeleWordDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Modeles de messages : lus par tout le module, ecrits par la Direction du Recouvrement. */
@RestController
@RequestMapping("/api/recouvrement/modeles-messages")
@PreAuthorize("hasAuthority('MODULE_RECOUVREMENT')")
@RequiredArgsConstructor
public class ModeleMessageRessource {

    private static final String MODULE = "RECOUVREMENT";
    private static final String DIRECTION = "hasAuthority('DROIT_MODELES_GERER')";

    private final ModeleMessageService modeleMessageService;
    private final ModeleWordService modeleWordService;

    @GetMapping
    public List<ModeleMessageDTO> rechercher(ModeleMessageCriteresDTO criteres) {
        return modeleMessageService.rechercher(MODULE, criteres);
    }

    @GetMapping("/{id}")
    public ModeleMessageDTO consulter(@PathVariable Long id) {
        return modeleMessageService.consulter(MODULE, id);
    }

    @PostMapping
    @PreAuthorize(DIRECTION)
    public ModeleMessageDTO creer(@Valid @RequestBody ModeleMessageDemandeDTO demande) {
        return modeleMessageService.creer(MODULE, demande);
    }

    @PutMapping("/{id}")
    @PreAuthorize(DIRECTION)
    public ModeleMessageDTO modifier(@PathVariable Long id, @Valid @RequestBody ModeleMessageDemandeDTO demande) {
        return modeleMessageService.modifier(MODULE, id, demande);
    }

    @PostMapping("/{id}/archivage")
    @PreAuthorize(DIRECTION)
    public ModeleMessageDTO archiver(@PathVariable Long id) {
        return modeleMessageService.archiver(MODULE, id);
    }

    @PostMapping("/{id}/reactivation")
    @PreAuthorize(DIRECTION)
    public ModeleMessageDTO reactiver(@PathVariable Long id) {
        return modeleMessageService.reactiver(MODULE, id);
    }

    @GetMapping("/{id}/word")
    public ResponseEntity<byte[]> exporterWord(@PathVariable Long id) {
        FichierDTO fichier = modeleWordService.exporter(MODULE, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fichier.getNom()).build().toString())
                .contentType(MediaType.parseMediaType(fichier.getTypeContenu()))
                .body(fichier.getContenu());
    }

    /** Pre-remplit le formulaire d'un nouveau modele ; rien n'est enregistre. */
    @PostMapping("/lecture-word")
    @PreAuthorize(DIRECTION)
    public LectureModeleWordDTO lireWord(@RequestParam("fichier") MultipartFile fichier) {
        return modeleWordService.lire(MODULE, fichier);
    }
}
