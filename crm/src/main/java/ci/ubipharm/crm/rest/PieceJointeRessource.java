package ci.ubipharm.crm.rest;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.PieceJointeService;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.PieceJointeDTO;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/marketing/pieces-jointes")
@PreAuthorize("hasAuthority('DROIT_CAMPAGNES_MARKETING')")
@RequiredArgsConstructor
public class PieceJointeRessource {

    private final PieceJointeService pieceJointeService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PieceJointeDTO deposerAffiche(@RequestPart("fichier") MultipartFile fichier) {
        return pieceJointeService.deposerAffiche(fichier);
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<byte[]> lire(@PathVariable Long id) {
        FichierDTO fichier = pieceJointeService.lire(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(fichier.getNom(), java.nio.charset.StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(MediaType.parseMediaType(fichier.getTypeContenu()))
                .body(fichier.getContenu());
    }
}
