package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un envoi WhatsApp en masse et son avancement. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EnvoiMasseDTO {
    private Long id;
    private String nom;
    private String numeroExpediteur;
    private String contenu;
    private String statut;
    private int nbDestinataires;
    private int nbExclus;
    private long nbEnAttente;
    private long nbEnvoyes;
    private long nbDistribues;
    private long nbLus;
    private long nbEchecs;
    private String creePar;
    private LocalDateTime creeLe;
    private String annulePar;
    private LocalDateTime termineLe;
    /** Vrai hors de la plage d'envoi WhatsApp : les messages attendent son ouverture. */
    private boolean horsPlage;
    /** Plage d'envoi, par ex. « 8 h et 18 h » (heure d'Abidjan). */
    private String plageHoraire;
    private Long modeleId;
    private String modeleNom;
    private LocalDateTime departLe;
    private List<PieceJointeDTO> piecesJointes;
}
