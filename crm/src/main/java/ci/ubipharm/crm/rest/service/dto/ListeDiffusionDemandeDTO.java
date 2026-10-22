package ci.ubipharm.crm.rest.service.dto;

import java.util.List;

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
public class ListeDiffusionDemandeDTO {

    @NotBlank(message = "Donnez un nom à la liste.")
    @Size(max = 150, message = "Le nom est trop long.")
    private String nom;
    @Size(max = 500, message = "La description est trop longue.")
    private String description;
    /** Faux : seuls les clients ajoutes a la main. */
    private boolean parCriteres;
    private List<Long> agenceIds;
    private List<String> sousAgences;
    private List<String> villes;
    private List<String> segments;
    private List<String> fonctions;
}
