package ci.ubipharm.crm.rest;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.RoleService;
import ci.ubipharm.crm.rest.service.dto.DroitDTO;
import ci.ubipharm.crm.rest.service.dto.RoleDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.RoleGestionDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Roles et droits : reserves a qui a le droit ROLES. */
@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasAuthority('DROIT_ROLES')")
@RequiredArgsConstructor
public class RoleRessource {

    private final RoleService roleService;

    @GetMapping
    public List<RoleGestionDTO> lister() {
        return roleService.lister();
    }

    @GetMapping("/droits")
    public List<DroitDTO> droits() {
        return roleService.droits();
    }

    @GetMapping("/{id}")
    public RoleGestionDTO consulter(@PathVariable Long id) {
        return roleService.consulter(id);
    }

    @PostMapping
    public RoleGestionDTO creer(@Valid @RequestBody RoleDemandeDTO demande) {
        return roleService.creer(demande);
    }

    @PutMapping("/{id}")
    public RoleGestionDTO modifier(@PathVariable Long id, @Valid @RequestBody RoleDemandeDTO demande) {
        return roleService.modifier(id, demande);
    }

    @PostMapping("/{id}/desactivation")
    public RoleGestionDTO desactiver(@PathVariable Long id) {
        return roleService.desactiver(id);
    }

    @PostMapping("/{id}/reactivation")
    public RoleGestionDTO reactiver(@PathVariable Long id) {
        return roleService.reactiver(id);
    }
}
