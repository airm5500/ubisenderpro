package ci.ubipharm.crm.rest;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.dto.ImportDTO;
import ci.ubipharm.crm.rest.service.dto.ParametresImportDTO;
import ci.ubipharm.crm.rest.service.dto.ValidationImportDTO;
import ci.ubipharm.crm.rest.service.ImportPromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/marketing/imports/promotions")
@PreAuthorize("hasAuthority('DROIT_PROMOTIONS_GERER')")
@RequiredArgsConstructor
public class ImportPromotionRessource {

    private final ImportPromotionService importPromotionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportDTO simuler(@RequestPart("fichier") MultipartFile fichier,
            @RequestPart(value = "parametres", required = false) ParametresImportDTO parametres) {
        return importPromotionService.simuler(fichier, parametres);
    }

    @PostMapping("/{id}/validation")
    public ImportDTO valider(@PathVariable Long id, @Valid @RequestBody ValidationImportDTO choix) {
        return importPromotionService.valider(id, choix);
    }
}
