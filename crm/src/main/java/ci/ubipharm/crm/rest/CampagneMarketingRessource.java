package ci.ubipharm.crm.rest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.CampagneMarketingService;
import ci.ubipharm.crm.rest.service.dto.AnalyseCampagneDTO;
import ci.ubipharm.crm.rest.service.dto.CampagneCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.CampagneMarketingDTO;
import ci.ubipharm.crm.rest.service.dto.CampagneMarketingDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.MessageCampagneCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.MessageCampagneDTO;
import ci.ubipharm.crm.rest.service.dto.MotifDTO;
import ci.ubipharm.crm.rest.service.dto.RecapitulatifMarketingDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Campagnes promotionnelles : preparees par le Marketing, validees par le directeur d'agence ou tout titulaire du droit. */
@RestController
@RequestMapping("/api/marketing/campagnes")
@PreAuthorize("hasAuthority('DROIT_CAMPAGNES_MARKETING')")
@RequiredArgsConstructor
public class CampagneMarketingRessource {

    private static final String AUTEURS = "hasAnyAuthority('DROIT_CAMPAGNES_MARKETING_PREPARER', 'DROIT_CAMPAGNES_MARKETING_VALIDER')";
    private static final String VALIDEURS = "hasAuthority('DROIT_CAMPAGNES_MARKETING_VALIDER')";

    private final CampagneMarketingService campagneMarketingService;

    @GetMapping
    public PageDTO<CampagneMarketingDTO> rechercher(@Valid CampagneCriteresDTO criteres) {
        return campagneMarketingService.rechercher(criteres);
    }

    @GetMapping("/{id}")
    public CampagneMarketingDTO consulter(@PathVariable Long id) {
        return campagneMarketingService.consulter(id);
    }

    @GetMapping("/{id}/analyse")
    public AnalyseCampagneDTO analyse(@PathVariable Long id) {
        return campagneMarketingService.analyse(id);
    }

    @GetMapping("/{id}/recapitulatif")
    public RecapitulatifMarketingDTO recapitulatif(@PathVariable Long id) {
        return campagneMarketingService.recapitulatif(id);
    }

    @GetMapping("/{id}/messages")
    public PageDTO<MessageCampagneDTO> messages(@PathVariable Long id, @Valid MessageCampagneCriteresDTO criteres) {
        return campagneMarketingService.messages(id, criteres);
    }

    @PostMapping
    @PreAuthorize(AUTEURS)
    public CampagneMarketingDTO creer(@Valid @RequestBody CampagneMarketingDemandeDTO demande) {
        return campagneMarketingService.creer(demande);
    }

    @PostMapping("/apercu")
    @PreAuthorize(AUTEURS)
    public RecapitulatifMarketingDTO apercu(@RequestBody CampagneMarketingDemandeDTO demande) {
        return campagneMarketingService.apercu(demande);
    }

    @PutMapping("/{id}")
    @PreAuthorize(AUTEURS)
    public CampagneMarketingDTO modifier(@PathVariable Long id, @Valid @RequestBody CampagneMarketingDemandeDTO demande) {
        return campagneMarketingService.modifier(id, demande);
    }

    @PostMapping("/{id}/soumission")
    @PreAuthorize(AUTEURS)
    public CampagneMarketingDTO soumettre(@PathVariable Long id) {
        return campagneMarketingService.soumettre(id);
    }

    @PostMapping("/{id}/validation")
    @PreAuthorize(VALIDEURS)
    public CampagneMarketingDTO valider(@PathVariable Long id) {
        return campagneMarketingService.valider(id);
    }

    @PostMapping("/{id}/rejet")
    @PreAuthorize(VALIDEURS)
    public CampagneMarketingDTO rejeter(@PathVariable Long id, @Valid @RequestBody MotifDTO motif) {
        return campagneMarketingService.rejeter(id, motif);
    }

    @PostMapping("/{id}/lancement")
    @PreAuthorize(VALIDEURS)
    public CampagneMarketingDTO lancer(@PathVariable Long id) {
        return campagneMarketingService.lancer(id);
    }

    @PostMapping("/{id}/annulation")
    @PreAuthorize(AUTEURS)
    public CampagneMarketingDTO annuler(@PathVariable Long id, @Valid @RequestBody MotifDTO motif) {
        return campagneMarketingService.annuler(id, motif);
    }
}
