package ci.ubipharm.crm.recouvrement.rest.service.dto;

import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "Saisissez le nom du modele.")
    @Size(max = 150, message = "Le nom ne peut pas depasser 150 caracteres.")
    private String nom;

    @NotBlank(message = "Choisissez le type de relance.")
    private String typeCampagne;

    @NotBlank(message = "Saisissez le texte du message.")
    @Size(max = 4000, message = "Le message ne peut pas depasser 4000 caracteres.")
    private String contenu;
}
