package ci.ubipharm.crm.rest.service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
public class PromotionProduitDTO {

    private Long id;
    private Long produitId;
    private String produitCode;
    private String produitLibelle;
    private String produitStatut;
    private String conditionnement;
    private BigDecimal prixPublic;
    private int quantiteMin;
    /** UG_QUANTITE, UG_POURCENTAGE ou PRIX. */
    private String avantage;
    private Integer ugQuantite;
    private BigDecimal ugPourcentage;
    private Integer ugPlafond;
    private BigDecimal prixPromo;
    /** L'avantage en clair, tel qu'il part dans les messages. */
    private String resume;
    private boolean actif;
    private boolean importe;
    private LocalDateTime modifieLe;
}
