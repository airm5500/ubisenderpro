package ci.ubipharm.crm.commun.rest.service.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImportDTO {

    private Long id;
    private String type;
    private String nomFichier;
    private String statut;
    private int nbLignes;
    private int nbCreations;
    private int nbMisesAJour;
    private int nbIdentiques;
    private int nbRejets;
    private Integer nbCrees;
    private Integer nbMisAJour;
    private Integer nbIgnores;
    private String auteur;
    private LocalDateTime creeLe;
    private String validePar;
    private LocalDateTime valideLe;
}
