package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Information client ou alerte operationnelle (livraison, garde, horaires...) annoncee par le Marketing. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InformationDTO {

    private Long id;
    private String titre;
    /** RETARD_LIVRAISON, MODIFICATION_TOURNEE, ANNULATION_TOURNEE, REPRISE_LIVRAISON, INFORMATION_GARDE, ... */
    private String typeInformation;
    /** NORMALE, IMPORTANTE, URGENTE ou CRITIQUE. */
    private String priorite;
    private String message;
    private Long agenceId;
    private String agence;
    private String tournee;
    private String signataire;
    /** Au-dela, l'information ne s'annonce plus. */
    private LocalDate finValidite;
    private LocalDate dateLivraison;
    private String creneau;
    private String heureInitiale;
    private String nouvelleHeure;
    private String causeCommunicable;
    /** Jamais envoyee au client. */
    private String causeInterne;
    private LocalDate dateResolution;
    private String jourFerie;
    private LocalDate dateGarde;
    private String heureLimiteCommande;
    private String consignesLivraison;
    private String pharmacienGarde;
    private String telephonePharmacien;
    private String statut;
    /** Expiree : fin de validite passee. */
    private boolean expiree;
    /** Campagnes qui l'annoncent, toutes periodes. */
    private int nbCampagnes;
    /** Derniere campagne qui l'annonce : nom et statut. */
    private String derniereCampagne;
    private String derniereCampagneStatut;
    private String creePar;
    private String modifiePar;
    private LocalDateTime modifieLe;
}
