package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Mesures agregees par le navigateur depuis son dernier envoi, pour un ecran (titre) ou un appel (methode et adresse). */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportMesureDTO {

    /** ECRAN ou API. */
    private String type;
    private String cle;
    private long nombre;
    private long totalMs;
    private long maxMs;
    /** Mesures d'au moins 1 s, puis d'au moins 3 s. */
    private long nb1s;
    private long nb3s;
    private long echecs;
}
