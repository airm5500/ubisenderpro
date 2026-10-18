package ci.ubipharm.crm.commun.rest.service.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Parametre modifiable par l'administrateur, avec son type et ses bornes. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ParametreDTO {

    private String cle;
    private String valeur;
    /** TEXTE, ENTIER, HEURE, EMAIL, URL ou COULEUR. */
    private String typeValeur;
    private Integer minimum;
    private Integer maximum;
    private boolean obligatoire;
    private String description;
    private String categorie;
    private String modifiePar;
    private LocalDateTime modifieLe;
}
