package ci.ubipharm.crm.rest.service.dto;

import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Memes valeurs appliquees a plusieurs clients ; seuls les champs cites dans « champs » changent, le reste de la fiche est intact. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MiseAJourSelectiveDTO {

    @NotEmpty(message = "Cochez au moins un client.")
    @Size(max = 2000, message = "2000 clients au plus par mise à jour.")
    private List<Long> clientIds;

    /** SEGMENT, AGENCE, LOCALISATION (region, ville, commune), TOURNEE, SOUS_AGENCE. */
    @NotEmpty(message = "Choisissez au moins un champ à modifier.")
    private Set<String> champs;

    /** Valeur vide = champ efface (sauf l'agence, toujours obligatoire). */
    private String segmentation;
    private Long agenceId;
    private Long regionId;
    private Long villeId;
    private Long communeId;
    private Long tourneeId;
    private String sousAgence;
}
