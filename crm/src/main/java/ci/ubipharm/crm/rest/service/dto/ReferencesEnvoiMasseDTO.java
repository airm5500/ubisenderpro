package ci.ubipharm.crm.rest.service.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ce que propose le formulaire d'envoi en masse : listes de diffusion, modeles WhatsApp actifs et variables remplies. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReferencesEnvoiMasseDTO {
    private List<ListeDiffusionDTO> listes;
    private List<ModeleMessageDTO> modeles;
    private List<VariableMessageDTO> variables;
}
