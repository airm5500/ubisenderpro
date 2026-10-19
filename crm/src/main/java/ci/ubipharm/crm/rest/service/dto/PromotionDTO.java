package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDate;
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
public class PromotionDTO {

    private Long id;
    private String code;
    private String nom;
    private String description;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    /** BROUILLON, ACTIVE, TERMINEE ou ARCHIVEE. */
    private String statut;
    private long nbProduits;
    /** La personne qui a cree la promotion en est la responsable. */
    private String creePar;
    private LocalDateTime creeLe;
    private String modifiePar;
    private LocalDateTime modifieLe;
}
