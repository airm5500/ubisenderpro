package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Campagne promotionnelle : une liste de diffusion, un modele du Marketing, un expediteur. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampagneMarketingDTO {

    private Long id;
    private String nom;
    private Long listeId;
    private String listeNom;
    private Long modeleId;
    private String modeleNom;
    private List<Long> promotionIds;
    /** « CODE · Nom » de chaque promotion citee. */
    private List<String> promotions;
    /** Produits de retour en stock cites par le message de disponibilite. */
    private List<Long> produitIds;
    /** « CODE · Libelle » de chaque produit cite. */
    private List<String> produits;
    /** PROMOTION ou EVENEMENT, selon le modele. */
    private String typeCampagne;
    /** Evenement du calendrier annonce (message de courtoisie). */
    private Long evenementId;
    private String evenementNom;
    /** Information client annoncee (livraison, garde, horaires, alerte). */
    private Long informationId;
    private String informationTitre;
    private String canal;
    private LocalDateTime dateExecution;
    private String recurrence;
    private Long afficheId;
    private String afficheNom;
    private String afficheType;
    private boolean joindreProduits;
    private Long listeProduitsId;
    private java.time.LocalDate finRecurrence;
    private int nbExecutions;
    private String statut;
    private int nbClients;
    private int nbMessages;
    private int nbExclus;
    private int nbEnvoyes;
    /** Pour les colonnes de la liste (remarque 5 du 22/10/2026) ; le detail est dans l'analyse. */
    private int nbDistribues;
    private int nbLus;
    private int nbEchecs;
    private Long creeParId;
    private String creePar;
    /** Numero WhatsApp de l'auteur, d'ou partent les messages WhatsApp. */
    private String numeroExpediteur;
    /** Agences des destinataires, connues des la soumission. */
    private List<Long> agencesCibles;
    private LocalDateTime createdAt;
    private LocalDateTime soumiseLe;
    private String validePar;
    private LocalDateTime valideLe;
    private String motifRejet;
    private String rejetePar;
    private LocalDateTime lanceLe;
    private LocalDateTime termineLe;
    private String annulePar;
    private LocalDateTime annuleLe;
    private String motifAnnulation;
}
