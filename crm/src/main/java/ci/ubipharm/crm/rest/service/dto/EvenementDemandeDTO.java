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
public class EvenementDemandeDTO {

    @NotBlank(message = "Saisissez le nom de l'événement.")
    @Size(max = 150, message = "Le nom ne peut pas dépasser 150 caractères.")
    private String nom;

    @NotBlank(message = "Choisissez la nature de l'événement.")
    private String nature;

    @NotNull(message = "Saisissez la date de l'événement.")
    private LocalDate dateDebut;

    private LocalDate dateFin;

    private boolean annuel;
}
