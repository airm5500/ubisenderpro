package ci.ubipharm.crm.rest.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Facture ou avoir saisi a la main ; la reference et le client ne changent plus apres la creation. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FactureDemandeDTO {

    @Size(max = 50)
    private String reference;
    private Long clientId;
    @NotNull
    private Long sousTypeId;
    /** FACTURE ou AVOIR ; les montants d'un avoir sont enregistres en negatif, comme a l'import. */
    private String nature;
    @NotNull
    private LocalDate dateFacture;
    @NotNull
    private LocalDate dateEcheance;
    @NotNull
    private BigDecimal montant;
    @NotNull
    private BigDecimal solde;
}
