package ci.ubipharm.crm.commun.rest.service.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Choix de l'utilisateur a la validation : les creations sont toujours appliquees ;
 * les mises a jour ne le sont que pour les cles listees, ou toutes si demande.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ValidationImportDTO {

    private boolean toutesLesMisesAJour;

    @Builder.Default
    private List<String> clesAMettreAJour = new ArrayList<>();
}
