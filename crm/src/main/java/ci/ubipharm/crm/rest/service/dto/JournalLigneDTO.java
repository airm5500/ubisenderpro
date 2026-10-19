package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Une ligne du journal des actions, lisible : qui, quand, quelle nature d'operation, sur quelle donnee. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JournalLigneDTO {
    /** Unique sur les deux sources : A + id d'action, C + id de connexion. */
    private String cle;
    private LocalDateTime le;
    private Long utilisateurId;
    private String login;
    private String utilisateurNom;
    private String action;
    private String actionLibelle;
    private String nature;
    private String natureLibelle;
    private String type;
    private String typeLibelle;
    private String entite;
    private Long entiteId;
    /** Libelle de l'objet concerne quand il se retrouve (code et nom du client, reference de facture...). */
    private String objet;
    /** Ecran de l'application ou ouvrir l'objet, sinon vide. */
    private String lien;
    private String details;
    private String adresseIp;
}
