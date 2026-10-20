package ci.ubipharm.crm.rest;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
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

import ci.ubipharm.crm.rest.service.CharteService;
import ci.ubipharm.crm.rest.service.dto.CharteDTO;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import lombok.RequiredArgsConstructor;

/** Nom, couleurs, logo et icone : lus sans connexion (ecran de connexion), modifies par l'administrateur. */
@RestController
@RequestMapping("/api/charte")
@RequiredArgsConstructor
public class CharteRessource {

    private final CharteService charteService;

    @GetMapping
    public CharteDTO charte() {
        return charteService.charte();
    }

    /** Logo ou icone deposes, sinon ceux livres avec l'application. */
    @GetMapping("/{code:logo|icone|banniere}")
    public ResponseEntity<byte[]> fichier(@PathVariable String code) {
        FichierDTO fichier = charteService.fichier(code);
        if (fichier == null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/logo.svg")).build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS))
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
                .contentType(MediaType.parseMediaType(fichier.getTypeContenu()))
                .body(fichier.getContenu());
    }

    @PostMapping(value = "/{code:logo|icone|banniere}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('DROIT_PARAMETRES')")
    public CharteDTO deposer(@PathVariable String code, @RequestPart("fichier") MultipartFile fichier) {
        return charteService.deposer(code, fichier);
    }

    @PostMapping("/{code:logo|icone|banniere}/reinitialisation")
    @PreAuthorize("hasAuthority('DROIT_PARAMETRES')")
    public CharteDTO reinitialiser(@PathVariable String code) {
        return charteService.reinitialiser(code);
    }
}
