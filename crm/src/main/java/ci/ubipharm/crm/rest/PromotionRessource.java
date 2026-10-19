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
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.PromotionService;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.PromotionCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.PromotionDTO;
import ci.ubipharm.crm.rest.service.dto.PromotionDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.PromotionProduitDTO;
import ci.ubipharm.crm.rest.service.dto.PromotionProduitDemandeDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Promotions du Marketing ; la liste sert aussi a qui prepare une campagne, pour y citer une promotion active. */
@RestController
@RequestMapping("/api/marketing/promotions")
@PreAuthorize("hasAnyAuthority('DROIT_PROMOTIONS', 'DROIT_CAMPAGNES_MARKETING')")
@RequiredArgsConstructor
public class PromotionRessource {

    private static final String GESTION = "hasAuthority('DROIT_PROMOTIONS_GERER')";

    private final PromotionService promotionService;

    @GetMapping
    public List<PromotionDTO> lister(@Valid PromotionCriteresDTO criteres) {
        return promotionService.lister(criteres);
    }

    @GetMapping("/{id:\\d+}")
    public PromotionDTO consulter(@PathVariable Long id) {
        return promotionService.consulter(id);
    }

    @GetMapping("/{id:\\d+}/produits")
    public List<PromotionProduitDTO> produits(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean avecRetires) {
        return promotionService.produits(id, avecRetires);
    }

    @GetMapping("/{id:\\d+}/produits/export")
    public ResponseEntity<byte[]> exporter(@PathVariable Long id) {
        FichierDTO fichier = promotionService.exporterProduits(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fichier.getNom()).build().toString())
                .contentType(MediaType.parseMediaType(fichier.getTypeContenu()))
                .body(fichier.getContenu());
    }

    @PostMapping
    @PreAuthorize(GESTION)
    public PromotionDTO creer(@Valid @RequestBody PromotionDemandeDTO demande) {
        return promotionService.creer(demande);
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize(GESTION)
    public PromotionDTO modifier(@PathVariable Long id, @Valid @RequestBody PromotionDemandeDTO demande) {
        return promotionService.modifier(id, demande);
    }

    @PostMapping("/{id:\\d+}/activation")
    @PreAuthorize(GESTION)
    public PromotionDTO activer(@PathVariable Long id) {
        return promotionService.activer(id);
    }

    @PostMapping("/{id:\\d+}/archivage")
    @PreAuthorize(GESTION)
    public PromotionDTO archiver(@PathVariable Long id) {
        return promotionService.archiver(id);
    }

    @PostMapping("/{id:\\d+}/reactivation")
    @PreAuthorize(GESTION)
    public PromotionDTO reactiver(@PathVariable Long id) {
        return promotionService.reactiver(id);
    }

    @PostMapping("/{id:\\d+}/produits")
    @PreAuthorize(GESTION)
    public PromotionProduitDTO ajouterProduit(@PathVariable Long id, @Valid @RequestBody PromotionProduitDemandeDTO demande) {
        return promotionService.ajouterProduit(id, demande);
    }

    @PutMapping("/{id:\\d+}/produits/{ligneId:\\d+}")
    @PreAuthorize(GESTION)
    public PromotionProduitDTO modifierProduit(@PathVariable Long id, @PathVariable Long ligneId,
            @Valid @RequestBody PromotionProduitDemandeDTO demande) {
        return promotionService.modifierProduit(id, ligneId, demande);
    }

    @PostMapping("/{id:\\d+}/produits/{ligneId:\\d+}/retrait")
    @PreAuthorize(GESTION)
    public PromotionProduitDTO retirerProduit(@PathVariable Long id, @PathVariable Long ligneId) {
        return promotionService.retirerProduit(id, ligneId);
    }
}
