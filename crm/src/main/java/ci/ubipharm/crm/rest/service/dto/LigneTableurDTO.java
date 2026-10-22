package ci.ubipharm.crm.rest.service.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Une ligne de fichier : valeurs en texte, indexees par l'en-tete normalise. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LigneTableurDTO {

    private int numero;
    private Map<String, String> valeurs;
}
