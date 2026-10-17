package ci.ubipharm.crm.commun.rest.service.dto;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ce que l'assistant montre apres le choix du fichier : colonnes lues et correspondance proposee. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApercuImportDTO {

    private String nomFichier;
    private int nbLignes;
    private List<ColonneApercuDTO> colonnes;
    private List<ChampImportDTO> champs;
    /** Champ de l'application -> cle de colonne du fichier. */
    private Map<String, String> correspondance;
    /** Champs obligatoires qu'aucune colonne ne remplit encore. */
    private List<String> champsManquants;
}
