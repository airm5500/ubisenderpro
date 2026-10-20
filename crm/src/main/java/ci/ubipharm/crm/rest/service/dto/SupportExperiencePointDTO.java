package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Point de la courbe : par tranche de 5 minutes jusqu'a 6 h de periode, par heure au-dela. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportExperiencePointDTO {

    private LocalDateTime le;
    private long appels;
    private long moyenneAppelMs;
    private long moyenneEcranMs;
    private double part3s;
}
