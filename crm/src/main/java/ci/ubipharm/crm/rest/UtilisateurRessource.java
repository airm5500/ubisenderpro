package ci.ubipharm.crm.rest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.UtilisateurService;
import ci.ubipharm.crm.rest.service.dto.CompteProvisoireDTO;
import ci.ubipharm.crm.rest.service.dto.ReferentielHabilitationDTO;
import ci.ubipharm.crm.rest.service.dto.UtilisateurCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.UtilisateurDTO;
import ci.ubipharm.crm.rest.service.dto.UtilisateurDemandeDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import jakarta.validation.Valid;
import ci.ubipharm.crm.rest.service.HistoriqueUtilisateurService;
import ci.ubipharm.crm.rest.service.dto.ActionJournalDTO;
import ci.ubipharm.crm.rest.service.dto.ConnexionHistoriqueDTO;
import ci.ubipharm.crm.rest.service.dto.HistoriqueCriteresDTO;
import lombok.RequiredArgsConstructor;

/** Comptes et habilitations : reserves a l'administrateur. */
@RestController
@RequestMapping("/api/utilisateurs")
@PreAuthorize("hasAuthority('DROIT_UTILISATEURS')")
@RequiredArgsConstructor
public class UtilisateurRessource {

    private final UtilisateurService utilisateurService;
    private final HistoriqueUtilisateurService historiqueUtilisateurService;

    @GetMapping
    public PageDTO<UtilisateurDTO> rechercher(@Valid UtilisateurCriteresDTO criteres) {
        return utilisateurService.rechercher(criteres);
    }

    @GetMapping("/referentiel")
    public ReferentielHabilitationDTO referentiel() {
        return utilisateurService.referentiel();
    }

    @GetMapping("/{id}/connexions")
    public PageDTO<ConnexionHistoriqueDTO> connexions(@PathVariable Long id, @Valid HistoriqueCriteresDTO criteres) {
        return historiqueUtilisateurService.connexions(id, criteres);
    }

    @GetMapping("/{id}/actions")
    public PageDTO<ActionJournalDTO> actions(@PathVariable Long id, @Valid HistoriqueCriteresDTO criteres) {
        return historiqueUtilisateurService.actions(id, criteres);
    }

    @GetMapping("/{id}")
    public UtilisateurDTO consulter(@PathVariable Long id) {
        return utilisateurService.consulter(id);
    }

    @PostMapping
    public CompteProvisoireDTO creer(@Valid @RequestBody UtilisateurDemandeDTO demande) {
        return utilisateurService.creer(demande);
    }

    @PutMapping("/{id}")
    public UtilisateurDTO modifier(@PathVariable Long id, @Valid @RequestBody UtilisateurDemandeDTO demande) {
        return utilisateurService.modifier(id, demande);
    }

    @PostMapping("/{id}/desactivation")
    public UtilisateurDTO desactiver(@PathVariable Long id) {
        return utilisateurService.desactiver(id);
    }

    @PostMapping("/{id}/reactivation")
    public UtilisateurDTO reactiver(@PathVariable Long id) {
        return utilisateurService.reactiver(id);
    }

    @PostMapping("/{id}/reinitialisation-mot-de-passe")
    public CompteProvisoireDTO reinitialiserMotDePasse(@PathVariable Long id) {
        return utilisateurService.reinitialiserMotDePasse(id);
    }
}
