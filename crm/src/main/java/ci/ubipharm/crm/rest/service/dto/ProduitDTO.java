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
public class ProduitDTO {

    private Long id;
    private String code;
    private String libelle;
    private String categorie;
    private String laboratoire;
    private String conditionnement;
    /** Prix public indicatif en FCFA, ou null. */
    private BigDecimal prix;
    private String description;
    private String statut;
    /** Vrai si la fiche vient (ou a ete mise a jour en dernier) d'un import. */
    private boolean importe;
    private String creePar;
    private LocalDateTime creeLe;
    private String modifiePar;
    private LocalDateTime modifieLe;
}
