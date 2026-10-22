package ci.ubipharm.crm.rest.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Une ligne de l'historique du suivi d'une facture (suivi vide = levee). */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SuiviFactureDTO {

    private Long id;
    private String suivi;
    private LocalDate datePromesse;
    private BigDecimal montantPromis;
    private String commentaire;
    private String par;
    private LocalDateTime le;
}
