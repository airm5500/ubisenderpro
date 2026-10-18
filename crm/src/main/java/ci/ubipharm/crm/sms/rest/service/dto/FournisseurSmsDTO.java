package ci.ubipharm.crm.sms.rest.service.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Fournisseur SMS tel que parametre ; la cle API n'est jamais renvoyee, seulement si elle est renseignee. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FournisseurSmsDTO {

    private Long id;
    private String code;
    private String libelle;
    private boolean actif;
    private boolean parDefaut;
    private String expediteur;
    private boolean cleRenseignee;
    private String formatNumero;
    private String urlEnvoi;
    private String methodeEnvoi;
    private String typeContenu;
    private String entetes;
    private String corps;
    private String cheminIdentifiant;
    private String cheminNbSms;
    private String cheminErreur;
    private String urlStatut;
    private String cheminStatut;
    private String champIdentifiantAccuse;
    private String champStatutAccuse;
    private String correspondanceStatuts;
    private String urlSolde;
    private String cheminSolde;
    /** Adresse a donner au fournisseur pour ses accuses ; null tant que application.url_publique est vide. */
    private String urlAccuse;
    private String modifiePar;
    private LocalDateTime modifieLe;
}
