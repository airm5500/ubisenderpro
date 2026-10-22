package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Issue d'un « Envoyer maintenant » : le statut du message apres l'essai et son motif eventuel. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EnvoiImmediatDTO {
    private String cle;
    private String statut;
    private String motif;
}
