package ci.ubipharm.crm.email.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Le canal e-mail est-il utilisable, et depuis quelle adresse. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EtatEmailDTO {

    /** Faux tant que le serveur SMTP n'est pas renseigne dans la configuration (spring.mail.host). */
    private boolean configure;
    private String expediteur;
    private long enAttente;
    private long envoyesAujourdhui;
    /** Plage d'envoi, heure d'Abidjan. */
    private int heureDebut;
    private int heureFin;
}
