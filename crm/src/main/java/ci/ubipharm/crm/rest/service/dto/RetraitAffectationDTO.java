package ci.ubipharm.crm.rest.service.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
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
public class RetraitAffectationDTO {

    @NotEmpty(message = "Choisissez au moins un client.")
    @Size(max = 5000, message = "5 000 clients au plus à la fois.")
    private List<Long> clientIds;

    @Size(max = 255, message = "Le motif ne peut pas dépasser 255 caractères.")
    private String motif;
}
