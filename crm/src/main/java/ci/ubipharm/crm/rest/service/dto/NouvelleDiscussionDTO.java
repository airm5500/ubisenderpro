package ci.ubipharm.crm.rest.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Premier message ecrit a un client depuis l'ecran Discussions ; sans expediteur, le numero de l'utilisateur. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NouvelleDiscussionDTO {
    private String numeroAgent;
    @NotBlank(message = "Saisissez le numéro WhatsApp du client.")
    private String numeroClient;
    @NotBlank(message = "Écrivez le message.")
    @Size(max = 4000, message = "Le message ne peut pas dépasser 4000 caractères.")
    private String contenu;
    /** Pieces jointes deposees, dans l'ordre : une 1re image porte le texte en legende. */
    private java.util.List<Long> pieceJointeIds;
}
