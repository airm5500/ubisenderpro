package ci.ubipharm.crm.rest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.EnvoiMasseService;
import ci.ubipharm.crm.rest.service.dto.DestinataireMasseDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiMasseApercuDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiMasseDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiMasseDemandeDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Envois WhatsApp en masse depuis le numero de l'utilisateur. */
@RestController
@RequestMapping("/api/envois-masse")
@PreAuthorize("hasAuthority('DROIT_ENVOI_MASSE')")
@RequiredArgsConstructor
public class EnvoiMasseRessource {

    private final EnvoiMasseService envoiMasseService;

    @GetMapping
    public PageDTO<EnvoiMasseDTO> rechercher(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int taille) {
        return envoiMasseService.rechercher(page, Math.min(taille, 200));
    }

    @GetMapping("/{id}")
    public EnvoiMasseDTO consulter(@PathVariable Long id) {
        return envoiMasseService.consulter(id);
    }

    @GetMapping("/{id}/destinataires")
    public PageDTO<DestinataireMasseDTO> destinataires(@PathVariable Long id, @RequestParam(required = false) String statut,
                                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int taille) {
        return envoiMasseService.destinataires(id, statut, page, Math.min(taille, 200));
    }

    @PostMapping
    public EnvoiMasseApercuDTO creer(@Valid @RequestBody EnvoiMasseDemandeDTO demande) {
        return envoiMasseService.creer(demande);
    }

    @PostMapping("/{id}/annulation")
    public EnvoiMasseDTO annuler(@PathVariable Long id) {
        return envoiMasseService.annuler(id);
    }
}
