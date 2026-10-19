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

/** Une discussion : un numero de l'entreprise et un numero de client, avec le dernier echange. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DiscussionDTO {
    private String numeroAgent;
    private String agentNom;
    private String numeroClient;
    private Long clientId;
    private String clientNom;
    private String nomAffiche;
    private String dernierMessage;
    /** ENTRANT ou SORTANT. */
    private String dernierSens;
    private LocalDateTime derniereActivite;
    private long nbRecus;
    /** L'utilisateur peut repondre depuis ce numero (le sien ou celui d'un agent qu'il remplace). */
    private boolean peutRepondre;
}
