package ci.ubipharm.crm.rest.service.dto;

import java.util.List;

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
public class ColonneApercuDTO {

    /** Cle a utiliser dans la correspondance (intitule normalise). */
    private String cle;
    /** Intitule tel qu'il figure dans le fichier. */
    private String libelle;
    private List<String> exemples;
}
