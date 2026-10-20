package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Nouvel envoi en masse : des numeros colles (numero ; nom), des clients choisis et/ou une liste de diffusion. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EnvoiMasseDemandeDTO {
    @NotBlank(message = "Donnez un nom à l'envoi.")
    @Size(max = 150, message = "Le nom ne peut pas dépasser 150 caractères.")
    private String nom;
    @NotBlank(message = "Écrivez le message.")
    @Size(max = 4000, message = "Le message ne peut pas dépasser 4000 caractères.")
    private String contenu;
    /** Une ligne par destinataire : numero, puis eventuellement le nom (separes par ; , ou tabulation). */
    private String numeros;
    private Long listeId;
    /** Clients choisis dans le selecteur : leurs numeros WhatsApp actifs (principal en tete). */
    private List<Long> clientIds;
    /** Modele dont le texte a servi de depart (garde pour l'historique). */
    private Long modeleId;
    /** Pieces jointes deposees, dans l'ordre d'envoi : la 1re image porte le texte en legende. */
    private List<Long> pieceJointeIds;
    /** Null : depart immediat ; sinon heure d'Abidjan (UTC) a partir de laquelle les messages partent. */
    private LocalDateTime departLe;
    /** true : verifier seulement (destinataires retenus et exclus), sans rien creer. */
    private boolean apercu;
}
