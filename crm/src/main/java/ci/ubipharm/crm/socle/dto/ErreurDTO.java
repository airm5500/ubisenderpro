package ci.ubipharm.crm.socle.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Corps de toutes les reponses d'erreur. Ne contient jamais de detail technique :
 * la reference permet de retrouver l'erreur complete dans les journaux du serveur.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ErreurDTO {

    private int statut;
    private String message;
    private String reference;
    private List<ChampEnErreurDTO> champs;
}
