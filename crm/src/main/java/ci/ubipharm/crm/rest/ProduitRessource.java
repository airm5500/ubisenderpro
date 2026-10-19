package ci.ubipharm.crm.rest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.ProduitService;
import ci.ubipharm.crm.rest.service.dto.FiltresCatalogueDTO;
import ci.ubipharm.crm.rest.service.dto.ProduitCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.ProduitDTO;
import ci.ubipharm.crm.rest.service.dto.ProduitDemandeDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/marketing/produits")
@PreAuthorize("hasAuthority('DROIT_PRODUITS')")
@RequiredArgsConstructor
public class ProduitRessource {

    private final ProduitService produitService;

    @GetMapping
    public PageDTO<ProduitDTO> rechercher(@Valid ProduitCriteresDTO criteres) {
        return produitService.rechercher(criteres);
    }

    @GetMapping("/filtres")
    public FiltresCatalogueDTO filtres() {
        return produitService.filtres();
    }

    @GetMapping("/{id:\\d+}")
    public ProduitDTO consulter(@PathVariable Long id) {
        return produitService.consulter(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('DROIT_PRODUITS_GERER')")
    public ProduitDTO creer(@Valid @RequestBody ProduitDemandeDTO demande) {
        return produitService.creer(demande);
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAuthority('DROIT_PRODUITS_GERER')")
    public ProduitDTO modifier(@PathVariable Long id, @Valid @RequestBody ProduitDemandeDTO demande) {
        return produitService.modifier(id, demande);
    }

    @PostMapping("/{id:\\d+}/archivage")
    @PreAuthorize("hasAuthority('DROIT_PRODUITS_GERER')")
    public ProduitDTO archiver(@PathVariable Long id) {
        return produitService.archiver(id);
    }

    @PostMapping("/{id:\\d+}/reactivation")
    @PreAuthorize("hasAuthority('DROIT_PRODUITS_GERER')")
    public ProduitDTO reactiver(@PathVariable Long id) {
        return produitService.reactiver(id);
    }

    @PostMapping("/{id:\\d+}/rupture")
    @PreAuthorize("hasAuthority('DROIT_PRODUITS_GERER')")
    public ProduitDTO rupture(@PathVariable Long id) {
        return produitService.changerDisponibilite(id, false);
    }

    @PostMapping("/{id:\\d+}/retour-stock")
    @PreAuthorize("hasAuthority('DROIT_PRODUITS_GERER')")
    public ProduitDTO retourStock(@PathVariable Long id) {
        return produitService.changerDisponibilite(id, true);
    }
}
