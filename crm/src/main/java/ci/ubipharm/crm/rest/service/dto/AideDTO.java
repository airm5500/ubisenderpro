package ci.ubipharm.crm.rest.service.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ce que l'ecran doit savoir pour guider l'utilisateur connecte. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AideDTO {

    /** Parametre aide.guidage : interrupteur de toute l'entreprise. */
    private boolean guidageEntreprise;
    /** Choix personnel de l'utilisateur connecte. */
    private boolean guidageUtilisateur;
    private List<AideTexteDTO> textes;
}
