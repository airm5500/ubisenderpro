package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Interim : le remplacant relance le portefeuille de l'absent depuis le numero de celui-ci. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DelegationDemandeDTO {

    @NotNull(message = "Choisissez l'agent absent.")
    private Long titulaireId;

    @NotNull(message = "Choisissez le remplaçant.")
    private Long remplacantId;

    @NotNull(message = "Saisissez la date de début.")
    private LocalDate dateDebut;

    @NotNull(message = "Saisissez la date de retour.")
    private LocalDate dateFin;

    @Size(max = 255, message = "Le motif ne peut pas dépasser 255 caractères.")
    private String motif;
}
