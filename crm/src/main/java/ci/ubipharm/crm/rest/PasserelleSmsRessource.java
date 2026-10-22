package ci.ubipharm.crm.rest;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.EnvoiSmsService;
import lombok.RequiredArgsConstructor;

/**
 * Accuses de reception des fournisseurs SMS : hors session et hors CSRF, authentifies par le jeton propre au fournisseur
 * place dans l'adresse donnee au fournisseur. Le corps (JSON) ou les parametres de l'adresse portent l'identifiant et le statut.
 */
@RestController
@RequestMapping("/api/passerelle/sms/{code}/accuse")
@RequiredArgsConstructor
public class PasserelleSmsRessource {

    private final EnvoiSmsService envoiSmsService;

    @PostMapping
    public ResponseEntity<Void> accusePoste(@PathVariable String code, @RequestParam Map<String, String> parametres,
            @RequestBody(required = false) String corps) {
        envoiSmsService.accuse(code, parametres.get("jeton"), corps, parametres);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Void> accuseLu(@PathVariable String code, @RequestParam Map<String, String> parametres) {
        envoiSmsService.accuse(code, parametres.get("jeton"), null, parametres);
        return ResponseEntity.noContent().build();
    }
}
