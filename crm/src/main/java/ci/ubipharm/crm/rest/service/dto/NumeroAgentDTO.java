package ci.ubipharm.crm.rest.service.dto;

import jakarta.validation.constraints.NotBlank;
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
public class NumeroAgentDTO {

    @NotBlank(message = "Saisissez le numéro WhatsApp professionnel.")
    private String numeroWhatsapp;
}
