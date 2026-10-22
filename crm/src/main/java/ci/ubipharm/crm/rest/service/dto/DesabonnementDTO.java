package ci.ubipharm.crm.rest.service.dto;

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
public class DesabonnementDTO {

    @Size(max = 255, message = "Le motif est trop long.")
    private String motif;
}
