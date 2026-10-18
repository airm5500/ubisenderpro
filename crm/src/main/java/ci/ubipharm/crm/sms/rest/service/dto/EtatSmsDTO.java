package ci.ubipharm.crm.sms.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Le canal SMS est-il utilisable, avec quel fournisseur et sous quel nom. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EtatSmsDTO {

    /** Faux tant qu'aucun fournisseur actif par defaut n'a sa cle API. */
    private boolean configure;
    private String fournisseur;
    private String expediteur;
    private long enAttente;
    private long envoyesAujourdhui;
    private int heureDebut;
    private int heureFin;
    /** Derniere erreur bloquante (cle refusee...), effacee au prochain envoi reussi. */
    private String alerte;
}
