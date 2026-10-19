package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Identite visuelle publique de l'application, lue avant la connexion. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CharteDTO {

    private String nom;
    private String couleurPrincipale;
    private String couleurSecondaire;
    /** Depot du logo affiche, null = logo livre avec l'application ; sert aussi a contourner le cache du navigateur. */
    private Long logoVersion;
    private String logoNom;
    private Long iconeVersion;
    private String iconeNom;
    private String siteWeb;
    /** WHATSAPP, EMAIL ou SMS : canal propose d'office pour un nouveau modele. */
    private String canalDefaut;
    /** WA_WEB ou META : mode propose d'office pour relier un nouveau numero. */
    private String modeEnvoiDefaut;
}
