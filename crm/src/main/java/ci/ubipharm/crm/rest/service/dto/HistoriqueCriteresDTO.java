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

/** Filtres de l'historique d'un utilisateur (connexions ou actions). */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HistoriqueCriteresDTO {

    private LocalDate du;
    private LocalDate au;
    /** Actions seulement : code ou debut de code (CLIENT_, CAMPAGNE_...). */
    private String action;
    /** Connexions seulement : true = reussies, false = echouees, vide = toutes. */
    private Boolean succes;

    @Min(value = 0, message = "La page doit être positive.")
    @Builder.Default
    private int page = 0;

    @Min(value = 1, message = "La taille doit être au moins 1.")
    @Max(value = 200, message = "La taille ne peut pas dépasser 200.")
    @Builder.Default
    private int taille = 50;
}
