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

/** Un message d'une discussion, recu ou envoye. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageDiscussionDTO {
    private String cle;
    /** ENTRANT ou SORTANT. */
    private String sens;
    /** RELANCE, PROMOTION, DISCUSSION, ENVOI_MASSE, ou REPONSE pour un message du client. */
    private String origine;
    private String source;
    private String type;
    private String contenu;
    private String statut;
    private String motif;
    private String auteur;
    private LocalDateTime le;
}
