package ci.ubipharm.crm.rest.service.dto;

import jakarta.validation.constraints.NotNull;
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
public class AidePreferenceDTO {

    @NotNull(message = "Précisez si le guidage est activé.")
    private Boolean actif;
}
