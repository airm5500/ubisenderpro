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

/** Un message envoye, quelle que soit son origine. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EnvoiHistoriqueDTO {
    /** C123 (campagne) ou S45 (discussion, envoi en masse). */
    private String cle;
    /** RELANCE, PROMOTION, DISCUSSION ou ENVOI_MASSE. */
    private String origine;
    /** Nom de la campagne ou de l'envoi en masse. */
    private String source;
    private String canal;
    private String expediteur;
    private String utilisateurNom;
    private String destinataire;
    private Long clientId;
    private String clientNom;
    private String contenu;
    private String statut;
    private String motif;
    private LocalDateTime le;
    private LocalDateTime distribueLe;
    private LocalDateTime luLe;
}
