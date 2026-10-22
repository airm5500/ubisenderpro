package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class PromotionDemandeDTO {

    @NotBlank(message = "Donnez le code de la promotion.")
    @Size(max = 30, message = "Le code est trop long (30 caractères au plus).")
    private String code;
    @NotBlank(message = "Donnez le nom de la promotion.")
    @Size(max = 150, message = "Le nom est trop long (150 caractères au plus).")
    private String nom;
    @Size(max = 1000, message = "La description est trop longue.")
    private String description;
    @NotNull(message = "Donnez la date de début.")
    private LocalDate dateDebut;
    @NotNull(message = "Donnez la date de fin.")
    private LocalDate dateFin;
}
