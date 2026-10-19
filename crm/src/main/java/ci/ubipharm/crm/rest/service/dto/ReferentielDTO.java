package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Region, ville, commune ou tournee : liste de reference des fiches clients. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReferentielDTO {

    private Long id;
    /** REGION, VILLE, COMMUNE ou TOURNEE. */
    private String type;
    private String code;
    private String libelle;
    /** Region d'une ville, ville d'une commune. */
    private Long parentId;
    private String parentLibelle;
    /** Agence desservie par une tournee. */
    private Long agenceId;
    private String agenceLibelle;
    private String statut;
    private long nbClients;
    private String modifiePar;
    private LocalDateTime modifieLe;
}
