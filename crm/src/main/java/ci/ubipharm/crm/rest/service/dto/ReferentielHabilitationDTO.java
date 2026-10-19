package ci.ubipharm.crm.rest.service.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Roles et modules proposes dans l'ecran des habilitations. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReferentielHabilitationDTO {

    private List<RoleDTO> roles;
    private List<ModuleDTO> modules;
}
