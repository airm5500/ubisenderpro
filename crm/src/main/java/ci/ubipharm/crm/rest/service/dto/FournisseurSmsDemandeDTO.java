package ci.ubipharm.crm.rest.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Creation ou modification d'un fournisseur SMS. Cle API vide en modification = cle actuelle gardee. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FournisseurSmsDemandeDTO {

    @NotBlank(message = "Saisissez le code du fournisseur.")
    @Pattern(regexp = "[A-Za-z0-9_]{2,30}", message = "Le code tient en 2 à 30 lettres, chiffres ou _.")
    private String code;

    @NotBlank(message = "Saisissez le nom du fournisseur.")
    @Size(max = 100, message = "Le nom tient en 100 caractères.")
    private String libelle;

    private boolean actif;

    @Size(max = 11, message = "Le nom d'expéditeur d'un SMS tient en 11 caractères.")
    private String expediteur;

    @Size(max = 500, message = "La clé API tient en 500 caractères.")
    private String cleApi;

    @Pattern(regexp = "INTERNATIONAL|PLUS|LOCAL", message = "Format de numéro inconnu.")
    private String formatNumero;

    @NotBlank(message = "Saisissez l'adresse d'envoi du fournisseur.")
    @Pattern(regexp = "https?://.+", message = "L'adresse d'envoi commence par https://.")
    @Size(max = 500)
    private String urlEnvoi;

    @Pattern(regexp = "POST|GET|PUT", message = "Méthode inconnue.")
    private String methodeEnvoi;

    @Size(max = 100)
    private String typeContenu;

    @Size(max = 2000, message = "Les en-têtes tiennent en 2000 caractères.")
    private String entetes;

    @Size(max = 4000, message = "Le corps tient en 4000 caractères.")
    private String corps;

    @Size(max = 200)
    private String cheminIdentifiant;
    @Size(max = 200)
    private String cheminNbSms;
    @Size(max = 200)
    private String cheminErreur;

    @Pattern(regexp = "|https?://.+", message = "L'adresse de statut commence par https://.")
    @Size(max = 500)
    private String urlStatut;
    @Size(max = 200)
    private String cheminStatut;
    @Size(max = 200)
    private String champIdentifiantAccuse;
    @Size(max = 200)
    private String champStatutAccuse;
    @Size(max = 1000)
    private String correspondanceStatuts;

    @Pattern(regexp = "|https?://.+", message = "L'adresse du solde commence par https://.")
    @Size(max = 500)
    private String urlSolde;
    @Size(max = 200)
    private String cheminSolde;
}
