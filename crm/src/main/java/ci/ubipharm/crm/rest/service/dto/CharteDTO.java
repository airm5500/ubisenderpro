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
    /** Nom de l'entreprise, repris par la variable {nom_entreprise} et dans les ecrans. */
    private String nomEntreprise;
    private String couleurPrincipale;
    private String couleurSecondaire;
    /** A, B ou C : habillage de la partie droite des ecrans. */
    private String theme;
    private String couleurFond;
    private String couleurSurvol;
    /** Sections du menu de gauche repliables, une seule ouverte a la fois. */
    private boolean menuRepliable;
    /** Depot du logo affiche, null = logo livre avec l'application ; sert aussi a contourner le cache du navigateur. */
    private Long logoVersion;
    private String logoNom;
    private Long iconeVersion;
    private String iconeNom;
    /** Banniere jointe en tete des messages d'information, null = aucune. */
    private Long banniereVersion;
    private String banniereNom;
    private String siteWeb;
    /** WHATSAPP, EMAIL ou SMS : canal propose d'office pour un nouveau modele. */
    private String canalDefaut;
    /** WA_WEB ou META : mode propose d'office pour relier un nouveau numero. */
    private String modeEnvoiDefaut;
}
