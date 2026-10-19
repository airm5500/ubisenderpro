package ci.ubipharm.crm.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.WebhookMetaService;
import lombok.RequiredArgsConstructor;

/** Webhook de l'API WhatsApp Cloud : hors session et hors CSRF, authentifie par le jeton de verification puis par la signature. */
@RestController
@RequestMapping("/api/passerelle/meta/webhook")
@RequiredArgsConstructor
public class PasserelleMetaRessource {

    private final WebhookMetaService webhookMetaService;

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> abonnement(@RequestParam(name = "hub.mode", required = false) String mode,
                                             @RequestParam(name = "hub.verify_token", required = false) String jeton,
                                             @RequestParam(name = "hub.challenge", required = false) String defi) {
        String reponse = webhookMetaService.verifierAbonnement(mode, jeton, defi);
        return reponse == null ? ResponseEntity.status(HttpStatus.FORBIDDEN).build() : ResponseEntity.ok(reponse);
    }

    @PostMapping
    public ResponseEntity<Void> notification(@RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
                                             @RequestBody String corps) {
        webhookMetaService.verifierSignature(corps, signature);
        webhookMetaService.traiter(corps);
        return ResponseEntity.ok().build();
    }
}
