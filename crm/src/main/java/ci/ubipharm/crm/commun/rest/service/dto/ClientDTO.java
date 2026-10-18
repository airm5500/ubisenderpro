package ci.ubipharm.crm.commun.rest.service.dto;

import java.util.Map;

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
    private String segmentationLibelle;
    private String nomPharmacien;
    private String ville;
    private Long agenceId;
    private String agenceLibelle;
    private String sousAgence;
    private String adresse;
    private String email;
    private String commentaire;
    private String numeroPrincipal;
    private String numeroSecondaire;
    private String statut;
    /** Numeros actifs par role (fiche client seulement), pour la modification. */
    private Map<String, String> numeros;
}
