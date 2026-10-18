package ci.ubipharm.crm.whatsapp.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Etat d'une session WhatsApp Web telle que le service la decrit ; qr = image a scanner (data URL). */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EtatSessionDTO {

    private String statut;
    private String sante;
    private String raison;
    private String qr;
}
