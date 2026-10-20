package ci.ubipharm.crm.rest;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.LectureDestinatairesDTO;
import ci.ubipharm.crm.rest.service.dto.PieceJointeDTO;
import ci.ubipharm.crm.rest.service.dto.ReferencesEnvoiMasseDTO;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.EnvoiMasseService;
import ci.ubipharm.crm.rest.service.dto.DestinataireMasseDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiMasseApercuDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiMasseDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiMasseDemandeDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Envois WhatsApp en masse depuis le numero de l'utilisateur. */
@RestController
@RequestMapping("/api/envois-masse")
@PreAuthorize("hasAuthority('DROIT_ENVOI_MASSE')")
@RequiredArgsConstructor
public class EnvoiMasseRessource {

    private final EnvoiMasseService envoiMasseService;

    @GetMapping
    public PageDTO<EnvoiMasseDTO> rechercher(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int taille) {
        return envoiMasseService.rechercher(page, Math.min(taille, 200));
    }

    @GetMapping("/{id}")
    public EnvoiMasseDTO consulter(@PathVariable Long id) {
        return envoiMasseService.consulter(id);
    }

    @GetMapping("/{id}/destinataires")
    public PageDTO<DestinataireMasseDTO> destinataires(@PathVariable Long id, @RequestParam(required = false) String statut,
                                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int taille) {
        return envoiMasseService.destinataires(id, statut, page, Math.min(taille, 200));
    }

    @PostMapping
    public EnvoiMasseApercuDTO creer(@Valid @RequestBody EnvoiMasseDemandeDTO demande) {
        return envoiMasseService.creer(demande);
    }

    @GetMapping("/references")
    public ReferencesEnvoiMasseDTO references() {
        return envoiMasseService.references();
    }

    @PostMapping(value = "/lecture-fichier", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LectureDestinatairesDTO lireFichier(@RequestPart("fichier") MultipartFile fichier) {
        return envoiMasseService.lireFichier(fichier);
    }

    @PostMapping(value = "/pieces-jointes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PieceJointeDTO deposerPieceJointe(@RequestPart("fichier") MultipartFile fichier) {
        return envoiMasseService.deposerPieceJointe(fichier);
    }

    @GetMapping("/{id}/pieces-jointes/{pieceId}")
    public ResponseEntity<byte[]> pieceJointe(@PathVariable Long id, @PathVariable Long pieceId) {
        FichierDTO fichier = envoiMasseService.pieceJointe(id, pieceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(fichier.getNom(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(fichier.getTypeContenu()))
                .body(fichier.getContenu());
    }

    @PostMapping("/{id}/relance-echecs")
    public EnvoiMasseDTO relancerEchecs(@PathVariable Long id) {
        return envoiMasseService.relancerEchecs(id);
    }

    @PostMapping("/{id}/annulation")
    public EnvoiMasseDTO annuler(@PathVariable Long id) {
        return envoiMasseService.annuler(id);
    }
}
