package ci.ubipharm.crm.rest.service.dto;

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
public class VerificationNumeroDTO {

    /** Tel que saisi ou enregistre. */
    private String saisi;
    /** Au format international (2250700000001), null si le format est faux. */
    private String numero;
    private Long clientId;
    private String client;
    /** Role du numero sur la fiche (PRINCIPAL, TITULAIRE...). */
    private String fonction;
    /** SUR_WHATSAPP, ABSENT, FORMAT_INVALIDE ou NON_VERIFIE. */
    private String resultat;
}
