package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Regle de delais : vide = toutes les agences, tous les segments, ou valeur reprise du niveau au-dessus. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DelaiRelanceDTO {

    private Long id;
    private Long agenceId;
    private String agenceLibelle;
    private String segment;
    private String segmentLibelle;
    /** Avant echeance : premier jour de relance (J-n). */
    private Integer avantJoursMax;
    /** Avant echeance : dernier jour de relance (J-n). */
    private Integer avantJoursMin;
    /** Apres echeance : premier jour de relance (J+n). */
    private Integer retardJoursMin;
    /** Jours minimum entre deux relances d'une meme facture. */
    private Integer intervalleJours;
    private String statut;
    /** L'utilisateur courant peut modifier ou archiver la regle (agence de son perimetre). */
    private boolean modifiable;
    private String modifiePar;
    private LocalDateTime modifieLe;
}
