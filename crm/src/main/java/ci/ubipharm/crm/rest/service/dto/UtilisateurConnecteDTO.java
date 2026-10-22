package ci.ubipharm.crm.rest.service.dto;

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
public class UtilisateurConnecteDTO {

    private Long id;
    private String login;
    private String nomComplet;
    private List<String> roles;
    private List<String> modules;
    /** Ecrans et actions permis, coches sur ses roles dans l'ecran Roles et droits. */
    private List<String> droits;
    /** Libelles de ses roles, pour le bas du menu. */
    private String profil;
    private boolean visionGlobale;
    private List<Long> agenceIds;
    /** Vrai tant que l'utilisateur n'a pas remplace le mot de passe donne par l'administrateur. */
    private boolean motDePasseProvisoire;
}
