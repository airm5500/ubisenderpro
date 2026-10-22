package ci.ubipharm.crm.rest.service.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
public class PromotionProduitDemandeDTO {

    @NotNull(message = "Choisissez un produit.")
    private Long produitId;
    /** Vide : des la premiere unite. */
    @Min(value = 1, message = "La quantité minimale est d'au moins 1.")
    private Integer quantiteMin;
    @NotNull(message = "Choisissez l'avantage.")
    @Pattern(regexp = "UG_QUANTITE|UG_POURCENTAGE|PRIX", message = "Avantage inconnu.")
    private String avantage;
    @Min(value = 1, message = "Offrez au moins une unité.")
    private Integer ugQuantite;
    private BigDecimal ugPourcentage;
    @Min(value = 1, message = "Le plafond est d'au moins une unité.")
    @Max(value = 100000, message = "Plafond trop élevé.")
    private Integer ugPlafond;
    private BigDecimal prixPromo;
}
