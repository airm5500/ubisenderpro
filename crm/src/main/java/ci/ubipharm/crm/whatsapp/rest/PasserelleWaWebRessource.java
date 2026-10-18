package ci.ubipharm.crm.whatsapp.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.whatsapp.rest.service.EvenementWhatsappService;
import ci.ubipharm.crm.whatsapp.rest.service.dto.EvenementAccuseDTO;
import ci.ubipharm.crm.whatsapp.rest.service.dto.EvenementMessageDTO;
import ci.ubipharm.crm.whatsapp.rest.service.dto.EvenementStatutDTO;
import lombok.RequiredArgsConstructor;

/** Callbacks du service wa-web : hors session et hors CSRF, authentifies par le jeton partage X-Api-Token. */
@RestController
@RequestMapping("/api/passerelle/wa-web")
@RequiredArgsConstructor
public class PasserelleWaWebRessource {

    private static final String JETON = "X-Api-Token";

    private final EvenementWhatsappService evenementWhatsappService;

    @PostMapping("/status")
    public ResponseEntity<Void> statut(@RequestHeader(value = JETON, required = false) String jeton, @RequestBody EvenementStatutDTO evenement) {
        evenementWhatsappService.verifierJeton(jeton);
        evenementWhatsappService.statut(evenement);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/ack")
    public ResponseEntity<Void> accuse(@RequestHeader(value = JETON, required = false) String jeton, @RequestBody EvenementAccuseDTO evenement) {
        evenementWhatsappService.verifierJeton(jeton);
        evenementWhatsappService.accuse(evenement);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/message")
    public ResponseEntity<Void> message(@RequestHeader(value = JETON, required = false) String jeton, @RequestBody EvenementMessageDTO evenement) {
        evenementWhatsappService.verifierJeton(jeton);
        evenementWhatsappService.message(evenement);
        return ResponseEntity.noContent().build();
    }
}
