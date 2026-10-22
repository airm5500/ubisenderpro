package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Compte cree ou reinitialise : le mot de passe provisoire n'est montre qu'une fois. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompteProvisoireDTO {

    private UtilisateurDTO utilisateur;
    private String motDePasseProvisoire;
}
