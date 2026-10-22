package ci.ubipharm.crm.rest.service.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Analyse d'une campagne (remarque 5 du 22/10/2026) : chiffres, taux et suivi jour par jour. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnalyseCampagneDTO {

    /** Messages prevus, hors clients ecartes. */
    private long cibles;
    private long envoyes;
    private long distribues;
    private long lus;
    private long echecs;
    private long enAttente;
    private long exclus;
    private long reponses;
    /** Distribues sur envoyes, en %, null tant que rien n'est parti. */
    private Double tauxDistribution;
    /** Lus sur distribues, en %. */
    private Double tauxLecture;
    /** Echecs sur messages tentes (envoyes + echecs), en %. */
    private Double tauxEchec;
    /** Reponses sur envoyes, en %. */
    private Double tauxReponse;
    private List<PointEvolutionDTO> courbe;
}
