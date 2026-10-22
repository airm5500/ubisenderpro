package ci.ubipharm.crm.rest.service.dto;

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
public class SegmentDTO {

    private Long id;
    private String code;
    private String libelle;
    /** Badge choisi dans le jeu fixe (COURONNE, TROPHEE, ETOILE...). */
    private String badge;
    private int ordre;
    private String statut;
    private long nbClients;
}
