package ci.ubipharm.crm.recouvrement.rest.service.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
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
public class FactureCriteresDTO {

    /** Reference de facture, code ou raison sociale du client. */
    private String q;
    private Long clientId;
    private Long agenceId;
    private Long sousTypeId;

    @Pattern(regexp = "[A-Z_]{1,20}", message = "Statut invalide.")
    private String statut;

    @Pattern(regexp = "FACTURE|AVOIR", message = "Nature inconnue.")
    private String nature;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate echeanceDu;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate echeanceAu;

    /** Uniquement les factures dont l'echeance est depassee et le solde non nul. */
    private boolean enRetard;

    @Min(value = 0, message = "La page doit être positive.")
    @Builder.Default
    private int page = 0;

    @Min(value = 1, message = "La taille doit être au moins 1.")
    @Max(value = 100, message = "La taille ne peut pas dépasser 100.")
    @Builder.Default
    private int taille = 25;
}
