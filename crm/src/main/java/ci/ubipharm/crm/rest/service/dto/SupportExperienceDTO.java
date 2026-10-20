package ci.ubipharm.crm.rest.service.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Experience utilisateur sur les dernieres heures : temps d'ouverture des ecrans et duree des appels vecus dans les navigateurs. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportExperienceDTO {

    private int heures;
    private boolean actif;
    /** Synthese des ecrans (cle ECRAN) et des appels (cle API). */
    private List<SupportExperienceLigneDTO> resume;
    /** 30 ecrans et 30 appels les plus lents en moyenne. */
    private List<SupportExperienceLigneDTO> ecrans;
    private List<SupportExperienceLigneDTO> appels;
    /** 15 appels qui echouent le plus. */
    private List<SupportExperienceLigneDTO> echecs;
    private List<SupportExperienceUtilisateurDTO> utilisateurs;
    private List<SupportExperiencePointDTO> serie;
}
