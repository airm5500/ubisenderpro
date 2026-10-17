package ci.ubipharm.crm.securite.rest.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.web.context.SecurityContextRepository;

import ci.ubipharm.crm.securite.rest.service.JournalConnexionService;
import ci.ubipharm.crm.securite.rest.service.PerimetreService;
import ci.ubipharm.crm.securite.rest.service.dto.ConnexionDTO;

class AuthServiceImplTest {

    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final SecurityContextRepository contextRepository = mock(SecurityContextRepository.class);
    private final JournalConnexionService journal = mock(JournalConnexionService.class);
    private final AuthServiceImpl service = new AuthServiceImpl(authenticationManager, contextRepository, journal,
            mock(PerimetreService.class));

    @Test
    void unCompteDesactiveRecoitLeMemeMessageQuUnMauvaisMotDePasse() {
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("compte desactive"));
        MockHttpServletRequest requete = new MockHttpServletRequest();
        requete.setRemoteAddr("10.0.0.5");

        assertThatThrownBy(() -> service.connecter(new ConnexionDTO("agent1", "x"), requete, new MockHttpServletResponse()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Echec de connexion");
        verify(journal).enregistrerEchec("agent1", "10.0.0.5", null);
        verify(contextRepository, never()).saveContext(any(), any(), any());
    }
}
