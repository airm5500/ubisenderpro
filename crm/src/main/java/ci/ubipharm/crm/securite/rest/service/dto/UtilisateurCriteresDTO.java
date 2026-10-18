package ci.ubipharm.crm.securite.rest.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Criteres de recherche, remplis par Spring a partir des query params. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UtilisateurCriteresDTO {

    @Size(max = 100, message = "La recherche est trop longue.")
    private String q;

    @Pattern(regexp = "ACTIF|DESACTIVE|ARCHIVE", message = "Statut inconnu.")
    private String statut;

    @Size(max = 50, message = "Rôle inconnu.")
    private String roleCode;

    private Long agenceId;

    @Min(value = 0, message = "La page doit être positive.")
    @Builder.Default
    private int page = 0;

    @Min(value = 1, message = "La taille doit être au moins 1.")
    @Max(value = 100, message = "La taille ne peut pas dépasser 100.")
    @Builder.Default
    private int taille = 50;
}
