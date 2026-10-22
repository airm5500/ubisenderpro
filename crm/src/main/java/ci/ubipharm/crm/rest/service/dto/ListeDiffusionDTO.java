package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;
import java.util.List;

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
public class ListeDiffusionDTO {

    private Long id;
    /** RECOUVREMENT ou MARKETING : une liste ne se voit que depuis son module. */
    private String module;
    private String nom;
    private String description;
    private boolean parCriteres;
    private List<Long> agenceIds;
    private List<String> sousAgences;
    private List<String> villes;
    private List<String> segments;
    /** Numeros du client retenus : PRINCIPAL, TITULAIRE... */
    private List<String> fonctions;
    private String statut;
    private long nbAjouts;
    private long nbExclusions;
    /** Clients de la liste (desabonnes et sans coordonnees compris), dans le perimetre du lecteur. */
    private long nbClients;
    /** Clients joignables : ni desabonnes, ni sans numero ou e-mail. */
    private long nbJoignables;
    private String creePar;
    private LocalDateTime creeLe;
    private LocalDateTime modifieLe;
}
