package ci.ubipharm.crm.rest;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.EnvoiImmediatService;
import ci.ubipharm.crm.rest.service.dto.EnvoiImmediatDTO;
import lombok.RequiredArgsConstructor;

/** « Envoyer maintenant » sur une ligne de message en attente ; le service controle l'auteur ou l'expediteur. */
@RestController
@RequestMapping("/api/envois")
@RequiredArgsConstructor
public class EnvoiImmediatRessource {

    private final EnvoiImmediatService envoiImmediatService;

    @PostMapping("/{cle}/immediat")
    public EnvoiImmediatDTO envoyer(@PathVariable String cle) {
        return envoiImmediatService.envoyer(cle);
    }
}
