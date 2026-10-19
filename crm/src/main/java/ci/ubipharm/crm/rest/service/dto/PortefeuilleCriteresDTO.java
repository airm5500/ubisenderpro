package ci.ubipharm.crm.rest.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class PortefeuilleCriteresDTO {

    private Long agentId;
    private Long agenceId;
    /** Code, raison sociale ou nom du pharmacien. */
    private String q;
    /** Uniquement les clients qui n'ont pas encore d'agent. */
    private boolean sansAgent;
    /** Uniquement les clients qui ont un solde a recouvrer. */
    private boolean avecSolde;

    /** ACTIF par defaut ; INACTIF pour la liste des clients desactives. */
    @jakarta.validation.constraints.Pattern(regexp = "ACTIF|INACTIF", message = "Statut inconnu.")
    @Builder.Default
    private String statut = "ACTIF";

    @Min(value = 0, message = "La page doit être positive.")
    @Builder.Default
    private int page = 0;

    @Min(value = 1, message = "La taille doit être au moins 1.")
    @Max(value = 200, message = "La taille ne peut pas dépasser 200.")
    @Builder.Default
    private int taille = 50;
}
