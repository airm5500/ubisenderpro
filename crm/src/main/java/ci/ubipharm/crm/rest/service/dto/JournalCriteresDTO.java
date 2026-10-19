package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Filtres du journal des actions ; tous facultatifs. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JournalCriteresDTO {

    private LocalDate du;
    private LocalDate au;
    /** Auteur de l'action ; 0 = traitements automatiques (aucun utilisateur). */
    private Long utilisateurId;
    /** Type de donnee : CLIENT, FACTURE, UTILISATEUR... (voir le referentiel du journal). */
    private String type;
    /** CREATION, MODIFICATION, DESACTIVATION, REACTIVATION, CONNEXION, ENVOI ou AUTRE. */
    private String nature;
    /** Texte libre : reference, code ou nom du client, login, detail. */
    @Size(max = 100, message = "La recherche ne peut pas dépasser 100 caractères.")
    private String q;
    /** Historique d'une fiche client : ses actions et celles de ses factures. */
    private Long clientId;

    @Min(value = 0, message = "La page doit être positive.")
    @Builder.Default
    private int page = 0;

    @Min(value = 1, message = "La taille doit être au moins 1.")
    @Max(value = 200, message = "La taille ne peut pas dépasser 200.")
    @Builder.Default
    private int taille = 50;
}
