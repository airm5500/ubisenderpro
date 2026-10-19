package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Numero WhatsApp d'un agent, etat de sa session et charge d'envoi du jour. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionAgentDTO {

    private Long agentId;
    private String agentNom;
    private String numero;
    private String statut;
    private String sante;
    private String raison;
    private LocalDateTime connecteLe;
    private long envoyesAujourdhui;
    private long enAttente;
}
