package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ticket d'assistance ; la conversation n'est remplie qu'a la consultation du ticket. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketDTO {

    private Long id;
    private String sujet;
    /** QUESTION, INCIDENT ou DEMANDE. */
    private String categorie;
    /** NORMALE, HAUTE ou URGENTE. */
    private String priorite;
    /** OUVERT, EN_COURS, RESOLU ou FERME. */
    private String statut;
    private Long auteurId;
    private String auteur;
    private Long assigneId;
    private String assigne;
    private long nbMessages;
    private LocalDateTime dernierMessageLe;
    private LocalDateTime creeLe;
    private LocalDateTime modifieLe;
    private List<SupportMessageDTO> messages;
}
