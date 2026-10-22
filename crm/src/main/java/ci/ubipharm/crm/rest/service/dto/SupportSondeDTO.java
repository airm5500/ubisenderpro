package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Resultat d'une sonde de sante : OK, ALERTE ou KO avec son explication. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportSondeDTO {

    private String code;
    private String libelle;
    /** OK, ALERTE ou KO. */
    private String etat;
    private String detail;
    private long dureeMs;
}
