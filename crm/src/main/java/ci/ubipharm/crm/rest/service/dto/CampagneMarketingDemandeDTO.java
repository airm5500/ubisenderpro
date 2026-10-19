package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Creation ou modification d'une campagne promotionnelle. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampagneMarketingDemandeDTO {

    @NotBlank(message = "Saisissez le nom de la campagne.")
    @Size(max = 150, message = "Le nom ne peut pas dépasser 150 caractères.")
    private String nom;

    @NotNull(message = "Choisissez la liste de diffusion.")
    private Long listeId;

    @NotNull(message = "Choisissez le modèle de message.")
    private Long modeleId;

    /** Promotions actives citees ; leurs variables se remplissent d'office. */
    private java.util.List<Long> promotionIds;

    /** Vide : execution des la validation. */
    private LocalDateTime dateExecution;
}
