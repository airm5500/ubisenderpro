package ci.ubipharm.crm.rest.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Une activite d'un jour du calendrier des tableaux de bord : un cumul (relances, encaissements...) ou un fait nomme (campagne, promotion...). */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActiviteCalendrierDTO {
    private LocalDate date;
    /**
     * Recouvrement : RELANCES, ECHECS, CAMPAGNE, CAMPAGNE_PROGRAMMEE, ENCAISSEMENT, PROMESSE, PROMESSE_ECHEANCE, ECHEANCE, REPONSES ;
     * Marketing : MESSAGES, ECHECS, CAMPAGNE, CAMPAGNE_PROGRAMMEE, REPONSES, DESABONNEMENTS, PROMOTION_DEBUT, PROMOTION_FIN, EVENEMENT.
     */
    private String type;
    private String libelle;
    private Long nombre;
    private BigDecimal montant;
    /** Ecran ou retrouver le detail, null = aucun. */
    private String lien;
}
