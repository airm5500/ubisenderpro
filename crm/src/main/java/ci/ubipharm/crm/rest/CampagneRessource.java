package ci.ubipharm.crm.rest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.CampagneService;
import ci.ubipharm.crm.rest.service.dto.AnalyseCampagneDTO;
import ci.ubipharm.crm.rest.service.dto.CampagneCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.CampagneDTO;
import ci.ubipharm.crm.rest.service.dto.CampagneDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.MessageCampagneCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.MessageCampagneDTO;
import ci.ubipharm.crm.rest.service.dto.MotifDTO;
import ci.ubipharm.crm.rest.service.dto.RecapitulatifCampagneDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Campagnes de relance : creees par les agents et les directions, validees par la Direction du Recouvrement. */
@RestController
@RequestMapping("/api/recouvrement/campagnes")
@PreAuthorize("hasAuthority('MODULE_RECOUVREMENT')")
@RequiredArgsConstructor
public class CampagneRessource {

    private static final String AUTEURS = "hasAnyAuthority('DROIT_CAMPAGNES_PREPARER', 'DROIT_CAMPAGNES_VALIDER')";
    private static final String VALIDEURS = "hasAuthority('DROIT_CAMPAGNES_VALIDER')";

    private final CampagneService campagneService;

    @GetMapping
    public PageDTO<CampagneDTO> rechercher(@Valid CampagneCriteresDTO criteres) {
        return campagneService.rechercher(criteres);
    }

    @GetMapping("/{id}")
    public CampagneDTO consulter(@PathVariable Long id) {
        return campagneService.consulter(id);
    }

    @GetMapping("/{id}/analyse")
    public AnalyseCampagneDTO analyse(@PathVariable Long id) {
        return campagneService.analyse(id);
    }

    @GetMapping("/{id}/recapitulatif")
    public RecapitulatifCampagneDTO recapitulatif(@PathVariable Long id) {
        return campagneService.recapitulatif(id);
    }

    @GetMapping("/{id}/messages")
    public PageDTO<MessageCampagneDTO> messages(@PathVariable Long id, @Valid MessageCampagneCriteresDTO criteres) {
        return campagneService.messages(id, criteres);
    }

    @PostMapping
    @PreAuthorize(AUTEURS)
    public CampagneDTO creer(@Valid @RequestBody CampagneDemandeDTO demande) {
        return campagneService.creer(demande);
    }

    @PutMapping("/{id}")
    @PreAuthorize(AUTEURS)
    public CampagneDTO modifier(@PathVariable Long id, @Valid @RequestBody CampagneDemandeDTO demande) {
        return campagneService.modifier(id, demande);
    }

    @PostMapping("/{id}/soumission")
    @PreAuthorize(AUTEURS)
    public CampagneDTO soumettre(@PathVariable Long id) {
        return campagneService.soumettre(id);
    }

    @PostMapping("/{id}/annulation")
    @PreAuthorize(AUTEURS)
    public CampagneDTO annuler(@PathVariable Long id, @Valid @RequestBody MotifDTO motif) {
        return campagneService.annuler(id, motif);
    }

    @PostMapping("/{id}/validation")
    @PreAuthorize(VALIDEURS)
    public CampagneDTO valider(@PathVariable Long id) {
        return campagneService.valider(id);
    }

    @PostMapping("/{id}/rejet")
    @PreAuthorize(VALIDEURS)
    public CampagneDTO rejeter(@PathVariable Long id, @Valid @RequestBody MotifDTO motif) {
        return campagneService.rejeter(id, motif);
    }

    @PostMapping("/{id}/lancement")
    @PreAuthorize(VALIDEURS)
    public CampagneDTO lancer(@PathVariable Long id) {
        return campagneService.lancer(id);
    }
}
