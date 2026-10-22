package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Message de la conversation d'un ticket. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportMessageDTO {

    private Long id;
    /** MESSAGE ou SYSTEME (changement de statut ou d'affectation). */
    private String type;
    private Long auteurId;
    private String auteur;
    /** Vrai si l'auteur n'est pas celui qui a ouvert le ticket (reponse du support). */
    private boolean support;
    private String contenu;
    private LocalDateTime creeLe;
}
