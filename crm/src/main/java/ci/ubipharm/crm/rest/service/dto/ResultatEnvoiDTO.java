package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Reponse du service a un envoi. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultatEnvoiDTO {

    private boolean succes;
    private String identifiant;
    private String erreur;
    /** Session absente ou deconnectee : le message attend la reconnexion. */
    private boolean sessionIndisponible;
    /** Service injoignable ou en erreur : nouvel essai plus tard. */
    private boolean erreurTechnique;
}
