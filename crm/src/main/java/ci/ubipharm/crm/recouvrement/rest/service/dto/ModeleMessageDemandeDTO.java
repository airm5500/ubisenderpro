package ci.ubipharm.crm.recouvrement.rest.service.dto;

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

    /** WHATSAPP, EMAIL ou SMS ; vide = WHATSAPP. */
    @Pattern(regexp = "WHATSAPP|EMAIL|SMS", message = "Canal inconnu.")
    private String canal;

    /** Objet de l'e-mail, obligatoire pour le canal EMAIL ; peut contenir des variables. */
    @Size(max = 200, message = "L'objet ne peut pas dépasser 200 caractères.")
    private String objet;

    @NotBlank(message = "Saisissez le texte du message.")
    @Size(max = 4000, message = "Le message ne peut pas dépasser 4000 caractères.")
    private String contenu;
}
