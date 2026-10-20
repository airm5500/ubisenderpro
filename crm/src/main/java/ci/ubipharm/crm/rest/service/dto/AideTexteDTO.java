package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Texte d'aide reecrit par l'administrateur ; les textes d'origine sont livres avec l'ecran. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AideTexteDTO {

    /** BOUTON (infobulle) ou PAGE (panneau « ? » d'un ecran). */
    private String type;
    private String cle;
    private String titre;
    private String texte;
    private String modifiePar;
    private LocalDateTime modifieLe;
}
