package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Piece jointe d'une campagne, sans son contenu. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PieceJointeDTO {

    private Long id;
    private String nature;
    private String nomFichier;
    private String typeMime;
    private int taille;
}
