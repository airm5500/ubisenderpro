package ci.ubipharm.crm.commun.rest;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.commun.rest.service.ImportClientService;
import ci.ubipharm.crm.commun.rest.service.dto.ImportDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ValidationImportDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/imports/clients")
@PreAuthorize("hasAnyRole('ADMIN', 'DIR_RECOUVREMENT', 'DIR_AGENCE')")
@RequiredArgsConstructor
public class ImportClientRessource {

    private final ImportClientService importClientService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportDTO simuler(@RequestParam("fichier") MultipartFile fichier) {
        return importClientService.simuler(fichier);
    }

    @PostMapping("/{id}/validation")
    public ImportDTO valider(@PathVariable Long id, @Valid @RequestBody ValidationImportDTO choix) {
        return importClientService.valider(id, choix);
    }
}
