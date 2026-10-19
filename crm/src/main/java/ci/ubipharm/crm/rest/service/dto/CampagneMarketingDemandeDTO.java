package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Creation ou modification d'une campagne promotionnelle. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampagneMarketingDemandeDTO {

    @NotBlank(message = "Saisissez le nom de la campagne.")
    @Size(max = 150, message = "Le nom ne peut pas dépasser 150 caractères.")
    private String nom;

    @NotNull(message = "Choisissez la liste de diffusion.")
    private Long listeId;

    @NotNull(message = "Choisissez le modèle de message.")
    private Long modeleId;

    /** Promotions actives citees ; leurs variables se remplissent d'office. */
    private java.util.List<Long> promotionIds;

    /** Produits de retour en stock annonces ({produits_disponibles}). */
    private java.util.List<Long> produitIds;

    /** Affiche deposee (image ou PDF) jointe a chaque message WhatsApp ou e-mail. */
    private Long afficheId;

    /** Joindre la liste Excel des produits des promotions et des produits de retour en stock cites. */
    private boolean joindreProduits;

    /** Vide : execution des la validation. */
    private LocalDateTime dateExecution;

    /** QUOTIDIENNE, HEBDOMADAIRE ou MENSUELLE ; vide : un seul envoi. */
    private String recurrence;

    /** Dernier jour ou la campagne peut repartir. */
    private java.time.LocalDate finRecurrence;
}
