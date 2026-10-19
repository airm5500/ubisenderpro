package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Reponse d'un client, rattachee si possible a la derniere relance qu'il a recue. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageRecuDTO {

    private Long id;
    private String numeroAgent;
    private String agentNom;
    private String numeroClient;
    private String nomAffiche;
    private Long clientId;
    private String clientCode;
    private String clientRaisonSociale;
    private String factureReference;
    private String campagneNom;
    private String type;
    private String contenu;
    private LocalDateTime recuLe;
}
