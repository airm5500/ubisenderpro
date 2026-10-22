package ci.ubipharm.crm.rest.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class DelaiRelanceDemandeDTO {

    private Long agenceId;
    private String segment;

    @Min(value = 1, message = "Le premier jour avant échéance est au moins J-1.")
    @Max(value = 90, message = "Le premier jour avant échéance est au plus J-90.")
    private Integer avantJoursMax;

    @Min(value = 1, message = "Le dernier jour avant échéance est au moins J-1.")
    @Max(value = 90, message = "Le dernier jour avant échéance est au plus J-90.")
    private Integer avantJoursMin;

    @Min(value = 0, message = "Le premier jour après échéance ne peut pas être négatif.")
    @Max(value = 365, message = "Le premier jour après échéance est au plus J+365.")
    private Integer retardJoursMin;

    @Min(value = 0, message = "L'intervalle entre deux relances ne peut pas être négatif.")
    @Max(value = 90, message = "L'intervalle entre deux relances est au plus de 90 jours.")
    private Integer intervalleJours;
}
