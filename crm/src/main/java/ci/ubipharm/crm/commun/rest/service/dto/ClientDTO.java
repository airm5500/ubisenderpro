package ci.ubipharm.crm.commun.rest.service.dto;

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
public class ClientDTO {

    private Long id;
    private String code;
    private String raisonSociale;
    private String segmentation;
    private String ville;
    private Long agenceId;
    private String agenceLibelle;
    private String numeroPrincipal;
    private String numeroSecondaire;
    private String statut;
}
