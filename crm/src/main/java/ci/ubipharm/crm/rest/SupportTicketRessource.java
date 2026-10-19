package ci.ubipharm.crm.rest;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.SupportTicketService;
import ci.ubipharm.crm.rest.service.dto.SupportAffectationDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.SupportIntervenantDTO;
import ci.ubipharm.crm.rest.service.dto.SupportMessageDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.SupportStatutDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.SupportTicketDTO;
import ci.ubipharm.crm.rest.service.dto.SupportTicketDemandeDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Tickets du centre de support : chacun ouvre et suit les siens, le titulaire du droit Support les traite tous. */
@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportTicketRessource {

    private static final String SUPPORT = "hasAuthority('DROIT_SUPPORT')";

    private final SupportTicketService supportTicketService;

    @GetMapping("/tickets")
    public List<SupportTicketDTO> mesTickets(@RequestParam(required = false) String statut) {
        return supportTicketService.mesTickets(statut);
    }

    @GetMapping("/tickets/tous")
    @PreAuthorize(SUPPORT)
    public List<SupportTicketDTO> tous(@RequestParam(required = false) String statut, @RequestParam(required = false) Long assigneId) {
        return supportTicketService.tous(statut, assigneId);
    }

    @GetMapping("/tickets/{id}")
    public SupportTicketDTO consulter(@PathVariable Long id) {
        return supportTicketService.consulter(id);
    }

    @PostMapping("/tickets")
    public SupportTicketDTO ouvrir(@Valid @RequestBody SupportTicketDemandeDTO demande) {
        return supportTicketService.ouvrir(demande);
    }

    @PostMapping("/tickets/{id}/messages")
    public SupportTicketDTO repondre(@PathVariable Long id, @Valid @RequestBody SupportMessageDemandeDTO demande) {
        return supportTicketService.repondre(id, demande);
    }

    @PostMapping("/tickets/{id}/statut")
    @PreAuthorize(SUPPORT)
    public SupportTicketDTO changerStatut(@PathVariable Long id, @Valid @RequestBody SupportStatutDemandeDTO demande) {
        return supportTicketService.changerStatut(id, demande.getStatut());
    }

    @PostMapping("/tickets/{id}/affectation")
    @PreAuthorize(SUPPORT)
    public SupportTicketDTO affecter(@PathVariable Long id, @RequestBody SupportAffectationDemandeDTO demande) {
        return supportTicketService.affecter(id, demande.getUtilisateurId());
    }

    @GetMapping("/intervenants")
    @PreAuthorize(SUPPORT)
    public List<SupportIntervenantDTO> intervenants() {
        return supportTicketService.intervenants();
    }
}
