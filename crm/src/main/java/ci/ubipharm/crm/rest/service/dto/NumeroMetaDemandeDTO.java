package ci.ubipharm.crm.rest.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Passage d'un numero a l'API officielle : son identifiant chez Meta (WhatsApp Manager). */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NumeroMetaDemandeDTO {

    @NotBlank(message = "Saisissez l'identifiant du numéro chez Meta (Phone number ID).")
    @Pattern(regexp = "\\d{5,30}", message = "L'identifiant du numéro chez Meta ne contient que des chiffres.")
    private String phoneNumberId;
}
