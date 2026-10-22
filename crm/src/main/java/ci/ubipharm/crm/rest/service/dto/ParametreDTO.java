package ci.ubipharm.crm.rest.service.dto;

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
    /** TEXTE, ENTIER, HEURE, EMAIL, URL, COULEUR, LIEN, CHOIX, OUI_NON ou DOSSIER. */
    private String typeValeur;
    private Integer minimum;
    private Integer maximum;
    /** Choix possibles d'un parametre CHOIX : CODE=Libelle|CODE=Libelle. */
    private String options;
    private boolean obligatoire;
    private String description;
    private String categorie;
    private String modifiePar;
    private LocalDateTime modifieLe;
}
