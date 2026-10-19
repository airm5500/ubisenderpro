package ci.ubipharm.crm.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.SessionWhatsappService;
import ci.ubipharm.crm.rest.service.dto.EtatSessionDTO;
import ci.ubipharm.crm.rest.service.dto.NumeroMetaDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.SessionAgentDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Liaison par QR du telephone professionnel de chaque agent, ou du Marketing pour ses campagnes. */
@RestController
@RequestMapping("/api/whatsapp/sessions")
@PreAuthorize("hasAnyAuthority('MODULE_RECOUVREMENT', 'DROIT_NUMERO_MARKETING')")
@RequiredArgsConstructor
public class SessionWhatsappRessource {

    private final SessionWhatsappService sessionWhatsappService;

    @GetMapping
    public List<SessionAgentDTO> sessions() {
        return sessionWhatsappService.sessions();
    }

    @GetMapping("/{agentId}")
    public EtatSessionDTO etat(@PathVariable Long agentId) {
        return sessionWhatsappService.etat(agentId);
    }

    @PostMapping("/{agentId}/connexion")
    public EtatSessionDTO connecter(@PathVariable Long agentId) {
        return sessionWhatsappService.connecter(agentId);
    }

    @PostMapping("/{agentId}/meta")
    @PreAuthorize("hasAuthority('DROIT_WHATSAPP_META')")
    public SessionAgentDTO passerMeta(@PathVariable Long agentId, @Valid @RequestBody NumeroMetaDemandeDTO demande) {
        return sessionWhatsappService.passerMeta(agentId, demande.getPhoneNumberId());
    }

    @PostMapping("/{agentId}/whatsapp-web")
    @PreAuthorize("hasAuthority('DROIT_WHATSAPP_META')")
    public SessionAgentDTO passerWhatsappWeb(@PathVariable Long agentId) {
        return sessionWhatsappService.passerWhatsappWeb(agentId);
    }

    @PostMapping("/{agentId}/deconnexion")
    public ResponseEntity<Void> deconnecter(@PathVariable Long agentId) {
        sessionWhatsappService.deconnecter(agentId);
        return ResponseEntity.noContent().build();
    }
}
