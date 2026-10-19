package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;
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
public class MembreListeDTO {

    private Long clientId;
    private String code;
    private String raisonSociale;
    private String nomPharmacien;
    private String agenceLibelle;
    private String segmentation;
    private String ville;
    /** CRITERES (retenu par les criteres), AJOUT ou EXCLUSION (a la main). */
    private String origine;
    private List<String> numeros;
    private String email;
    /** DESABONNE, SANS_COORDONNEES ou null (joignable). */
    private String motifExclusion;
    private LocalDateTime desabonneLe;
}
