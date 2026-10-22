package ci.ubipharm.crm.rest.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Creation ou modification d'un modele de message. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ModeleMessageDemandeDTO {

    @NotBlank(message = "Saisissez le nom du modèle.")
    @Size(max = 150, message = "Le nom ne peut pas dépasser 150 caractères.")
    private String nom;

    @NotBlank(message = "Choisissez le type de relance.")
    private String typeCampagne;

    /** Modele d'information (Marketing) : type d'information qu'il annonce, pour le proposer d'office. */
    private String typeInformation;

    /** WHATSAPP, EMAIL ou SMS ; vide = WHATSAPP. */
    @Pattern(regexp = "WHATSAPP|EMAIL|SMS", message = "Canal inconnu.")
    private String canal;

    /** Objet de l'e-mail, obligatoire pour le canal EMAIL ; peut contenir des variables. */
    @Size(max = 200, message = "L'objet ne peut pas dépasser 200 caractères.")
    private String objet;

    @NotBlank(message = "Saisissez le texte du message.")
    @Size(max = 4000, message = "Le message ne peut pas dépasser 4000 caractères.")
    private String contenu;

    /** Nom du modele approuve par Meta (API officielle, hors fenetre de 24 h) ; {{1}}, {{2}}... = variables dans l'ordre. */
    @Pattern(regexp = "[a-z0-9_]{1,512}", message = "Le nom du modèle Meta s'écrit en minuscules, chiffres et _ (ex. relance_echeance).")
    private String metaModele;

    @Pattern(regexp = "[a-z]{2,3}(_[A-Z]{2})?", message = "La langue du modèle Meta s'écrit comme fr ou fr_FR.")
    private String metaLangue;

    /** AUCUN, IMAGE ou DOCUMENT : en-tete du modele Meta, rempli par la premiere piece jointe de ce type. */
    @Pattern(regexp = "AUCUN|IMAGE|DOCUMENT", message = "En-tête du modèle Meta inconnu.")
    private String metaEntete;
}
