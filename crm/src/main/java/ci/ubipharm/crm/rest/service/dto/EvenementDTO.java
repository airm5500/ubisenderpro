package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Evenement du calendrier (fete, jour ferie, fermeture d'agence) annonce aux clients par le Marketing. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvenementDTO {

    private Long id;
    private String nom;
    /** FETE, JOUR_FERIE, FERMETURE ou AUTRE. */
    private String nature;
    private LocalDate dateDebut;
    /** Dernier jour ; vide : un seul jour. */
    private LocalDate dateFin;
    /** Revient chaque annee a la meme date. */
    private boolean annuel;
    private String statut;
    /** Prochaine occurrence (aujourd'hui compris) ; vide si l'evenement est passe. */
    private LocalDate prochaineDate;
    private LocalDate prochaineFin;
    /** Envoi propose pour la prochaine occurrence (parametres marketing.evenement_*). */
    private LocalDateTime envoiPropose;
    private String modifiePar;
    private LocalDateTime modifieLe;
}
