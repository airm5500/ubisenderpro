package ci.ubipharm.crm.rest.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un point des courbes : le jour (ou le premier jour de la semaine) et les valeurs de cet intervalle. Le recouvrement
 * renseigne relances, encaissements et solde echu ; le marketing renseigne messages, reponses et desabonnements ;
 * l'historique des envois renseigne envois, lectures et echecs.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PointEvolutionDTO {

    private LocalDate date;
    private long envoyes;
    private long distribues;
    private long lus;
    private long echecs;
    private long reponses;
    private long desabonnements;
    private BigDecimal montantEncaisse;
    /** Solde du echu a la fin de l'intervalle (recouvrement), tel que le CRM le connaissait alors. */
    private BigDecimal soldeEchu;
}
