package ci.ubipharm.crm.securite.rest.service.dto;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Utilisateur tel que Spring Security le garde en session. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UtilisateurPrincipal implements UserDetails {

    private Long id;
    private String login;
    private String nomComplet;
    private String motDePasseHash;
    private boolean actif;
    private Collection<? extends GrantedAuthority> authorities;

    @Override
    public String getUsername() {
        return login;
    }

    @Override
    public String getPassword() {
        return motDePasseHash;
    }

    @Override
    public boolean isEnabled() {
        return actif;
    }
}
