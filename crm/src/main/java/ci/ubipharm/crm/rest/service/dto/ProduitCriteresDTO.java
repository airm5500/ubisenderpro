package ci.ubipharm.crm.rest.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
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
public class ProduitCriteresDTO {

    /** Code, libelle ou laboratoire. */
    @Size(max = 100, message = "La recherche est trop longue.")
    private String q;
    private String categorie;
    private String laboratoire;

    @Pattern(regexp = "ACTIF|ARCHIVE|TOUS", message = "Statut inconnu.")
    @Builder.Default
    private String statut = "ACTIF";

    /** DISPONIBLE, RUPTURE ou RETOUR (remis en stock depuis peu). */
    @jakarta.validation.constraints.Pattern(regexp = "DISPONIBLE|RUPTURE|RETOUR", message = "Disponibilité inconnue.")
    private String disponibilite;

    @Min(value = 0, message = "La page doit être positive.")
    @Builder.Default
    private int page = 0;

    @Min(value = 1, message = "La taille doit être au moins 1.")
    @Max(value = 100, message = "La taille ne peut pas dépasser 100.")
    @Builder.Default
    private int taille = 25;
}
