package ci.ubipharm.crm.rest.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FactureDTO {

    private Long id;
    private String reference;
    private Long clientId;
    private String clientCode;
    private String clientRaisonSociale;
    private Long agenceId;
    private String agenceLibelle;
    private Long sousTypeId;
    private String sousTypeLibelle;
    private String nature;
    private LocalDate dateFacture;
    private LocalDate dateEcheance;
    private BigDecimal montant;
    private BigDecimal solde;
    private String statut;
    /** PROMESSE, LITIGE ou CONTENTIEUX ; vide = relances normales. */
    private String suivi;
    private LocalDate datePromesse;
    private BigDecimal montantPromis;
    private String suiviCommentaire;
    private String suiviPar;
    private java.time.LocalDateTime suiviLe;
    /** Relances suspendues aujourd'hui (litige, contentieux ou promesse dont la date n'est pas passee). */
    private boolean relancesSuspendues;
    /** Jours depuis l'echeance, 0 si elle n'est pas depassee. */
    private int joursRetard;
}
