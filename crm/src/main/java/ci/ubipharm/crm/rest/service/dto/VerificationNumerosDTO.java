package ci.ubipharm.crm.rest.service.dto;

import java.util.List;

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
public class VerificationNumerosDTO {

    /** Numero WhatsApp Web qui a interroge WhatsApp, null si aucun n'etait connecte. */
    private String sessionUtilisee;
    private String avertissement;
    private int surWhatsapp;
    private int absents;
    private int formatsInvalides;
    private int nonVerifies;
    private List<VerificationNumeroDTO> resultats;
}
