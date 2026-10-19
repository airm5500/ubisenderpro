package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Creation ou modification d'une campagne de relance. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampagneDemandeDTO {

    @NotBlank(message = "Saisissez le nom de la campagne.")
    @Size(max = 150, message = "Le nom ne peut pas dépasser 150 caractères.")
    private String nom;

    @NotBlank(message = "Choisissez le type de relance.")
    private String typeCampagne;

    @NotNull(message = "Choisissez le modèle de message.")
    private Long modeleId;

    @Valid
    private CriteresCampagneDTO criteres;

    /** Vide : execution des la validation. */
    private LocalDateTime dateExecution;
}
