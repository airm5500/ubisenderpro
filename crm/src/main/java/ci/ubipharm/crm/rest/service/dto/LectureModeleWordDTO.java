package ci.ubipharm.crm.rest.service.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Modele lu dans un fichier Word : pre-remplit le formulaire, rien n'est enregistre. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LectureModeleWordDTO {
    private ModeleMessageDemandeDTO modele;
    /** Ce qui n'a pas pu etre repris tel quel (images, tableaux, texte coupe...). */
    private List<String> avertissements;
}
