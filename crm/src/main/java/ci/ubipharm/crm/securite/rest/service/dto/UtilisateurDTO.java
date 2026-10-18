package ci.ubipharm.crm.securite.rest.service.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Compte utilisateur vu par l'administrateur, avec ses habilitations et ses agences en cours. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UtilisateurDTO {

    private Long id;
    private String login;
    private String nomComplet;
    private String email;
    private String numeroWhatsapp;
    private String statut;
    private boolean motDePasseProvisoire;
    private int echecsConnexion;
    private LocalDateTime derniereConnexion;
    private LocalDateTime createdAt;
    private List<HabilitationDTO> habilitations;
    private List<AgenceUtilisateurDTO> agences;
}
