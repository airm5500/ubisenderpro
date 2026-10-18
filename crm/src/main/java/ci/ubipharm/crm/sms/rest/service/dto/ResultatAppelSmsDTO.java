package ci.ubipharm.crm.sms.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Reponse du fournisseur a un essai d'envoi ou a une demande de solde, telle que lue par le CRM. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultatAppelSmsDTO {

    private boolean reussi;
    private Integer statutHttp;
    private String identifiant;
    private Integer nbSms;
    /** Solde lu, pour une demande de solde. */
    private String solde;
    /** Message d'erreur lu dans la reponse, ou cause de l'echec. */
    private String erreur;
    /** Debut de la reponse brute, pour regler les chemins de lecture. */
    private String reponse;
}
