package ci.ubipharm.crm.whatsapp.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.whatsapp.rest.service.SessionWhatsappService;
import ci.ubipharm.crm.whatsapp.rest.service.dto.EtatSessionDTO;
import ci.ubipharm.crm.whatsapp.rest.service.dto.SessionAgentDTO;
import lombok.RequiredArgsConstructor;

/** Liaison par QR du telephone professionnel de chaque agent. */
@RestController
@RequestMapping("/api/whatsapp/sessions")
@PreAuthorize("hasAuthority('MODULE_RECOUVREMENT')")
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

    @PostMapping("/{agentId}/deconnexion")
    public ResponseEntity<Void> deconnecter(@PathVariable Long agentId) {
        sessionWhatsappService.deconnecter(agentId);
        return ResponseEntity.noContent().build();
    }
}
