package ci.ubipharm.crm.recouvrement.rest.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Message d'une facture : genere a la volee avant validation, fige ensuite. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageCampagneDTO {

    private Long id;
    private Long factureId;
    private String factureReference;
    private LocalDate dateEcheance;
    private BigDecimal solde;
    private Long clientId;
    private String clientCode;
    private String clientRaisonSociale;
    private Long agenceId;
    private String canal;
    private String numeroDestinataire;
    private String emailDestinataire;
    private Long agentId;
    private String agentNom;
    private String numeroExpediteur;
    /** Adresse de reponse (Reply-To) d'un e-mail : celle de l'agent. */
    private String emailReponse;
    private String objet;
    private String contenu;
    private String statut;
    private String motif;
    private LocalDateTime envoyeLe;
    private LocalDateTime distribueLe;
    private LocalDateTime luLe;
}
