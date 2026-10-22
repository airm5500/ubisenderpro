package ci.ubipharm.crm.rest.service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Champ de l'application qu'un import sait remplir, et les intitules de colonne qui le designent. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChampImportDTO {

    private String code;
    private String libelle;
    private boolean obligatoire;
    private String exemple;

    @JsonIgnore
    private List<String> alias;
}
