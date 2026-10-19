package ci.ubipharm.crm.rest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.ImportService;
import ci.ubipharm.crm.rest.service.dto.ImportCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.ImportDTO;
import ci.ubipharm.crm.rest.service.dto.ImportLigneCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.ImportLigneDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/imports")
@PreAuthorize("hasAnyAuthority('DROIT_IMPORTS', 'DROIT_PRODUITS_GERER', 'DROIT_PROMOTIONS_GERER')")
@RequiredArgsConstructor
public class ImportRessource {

    private final ImportService importService;

    @GetMapping
    public PageDTO<ImportDTO> historique(@Valid ImportCriteresDTO criteres) {
        return importService.historique(criteres);
    }

    @GetMapping("/{id}")
    public ImportDTO consulter(@PathVariable Long id) {
        return importService.consulter(id);
    }

    @GetMapping("/{id}/lignes")
    public PageDTO<ImportLigneDTO> lignes(@PathVariable Long id, @Valid ImportLigneCriteresDTO criteres) {
        return importService.lignes(id, criteres);
    }

    @PostMapping("/{id}/annulation")
    public ImportDTO annuler(@PathVariable Long id) {
        return importService.annuler(id);
    }
}
