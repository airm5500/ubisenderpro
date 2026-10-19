package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Campagne de relance et son journal (cahier des charges 4.14). */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampagneDTO {

    private Long id;
    private String nom;
    private String typeCampagne;
    private Long modeleId;
    private String modeleNom;
    /** Canal du modele : WHATSAPP, EMAIL ou SMS. */
    private String canal;
    private CriteresCampagneDTO criteres;
    private LocalDateTime dateExecution;
    private String recurrence;
    private java.time.LocalDate finRecurrence;
    private int nbExecutions;
    private String statut;
    private int nbClients;
    private int nbFactures;
    private int nbMessages;
    private int nbExclus;
    private int nbEnvoyes;
    private int nbEchecs;
    private Long creeParId;
    private String creePar;
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
