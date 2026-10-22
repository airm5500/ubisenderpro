package ci.ubipharm.crm.rest.service.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class ProduitDemandeDTO {

    @NotBlank(message = "Donnez le code du produit.")
    @Size(max = 50, message = "Le code est trop long (50 caractères au plus).")
    private String code;
    @NotBlank(message = "Donnez le libellé du produit.")
    @Size(max = 200, message = "Le libellé est trop long (200 caractères au plus).")
    private String libelle;
    @Size(max = 100, message = "La catégorie est trop longue.")
    private String categorie;
    @Size(max = 150, message = "Le laboratoire est trop long.")
    private String laboratoire;
    @Size(max = 100, message = "Le conditionnement est trop long.")
    private String conditionnement;
    @DecimalMin(value = "0", message = "Le prix ne peut pas être négatif.")
    @Digits(integer = 12, fraction = 2, message = "Prix invalide.")
    private BigDecimal prix;
    @Size(max = 1000, message = "La description est trop longue.")
    private String description;
}
