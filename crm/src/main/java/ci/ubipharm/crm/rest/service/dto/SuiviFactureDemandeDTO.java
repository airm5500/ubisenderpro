package ci.ubipharm.crm.rest.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Pose ou levee du suivi d'une facture : suivi vide = retour aux relances normales. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SuiviFactureDemandeDTO {

    @Pattern(regexp = "PROMESSE|LITIGE|CONTENTIEUX", message = "Suivi inconnu.")
    private String suivi;

    /** Promesse : date a laquelle le client s'engage a payer. */
    private LocalDate datePromesse;

    /** Promesse : montant promis, facultatif (le solde par defaut). */
    private BigDecimal montantPromis;

    @Size(max = 500, message = "Le commentaire ne peut pas dépasser 500 caractères.")
    private String commentaire;
}
