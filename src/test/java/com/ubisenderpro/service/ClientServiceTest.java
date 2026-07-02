package com.ubisenderpro.service;

import com.ubisenderpro.entity.Client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Validation de la fiche compte client : champs obligatoires, format d'e-mail,
 * unicité du numéro client — toujours avec un message clair et le champ fautif
 * (jamais d'« erreur technique » pour une erreur de saisie).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientServiceTest {

    @Mock
    private EntityManager em;
    @Mock
    private ReferentielGeoService referentielGeoService;

    @InjectMocks
    private ClientService service;

    @SuppressWarnings("unchecked")
    private void clientsPortantCeNumero(List<Client> resultat) {
        TypedQuery<Client> q = mock(TypedQuery.class);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.setMaxResults(anyInt())).thenReturn(q);
        when(q.getResultList()).thenReturn(resultat);
        when(em.createQuery(anyString(), eq(Client.class))).thenReturn(q);
    }

    private Client clientValide() {
        Client c = new Client();
        c.setNumeroClient("C-0042");
        c.setNomCompte("PHARMACIE DU PLATEAU");
        return c;
    }

    @Test
    void numeroClientObligatoire() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.creer(new Client()));
        assertEquals("numeroClient", ex.getChamp());
        assertTrue(ex.getMessage().toLowerCase().contains("obligatoire"));
    }

    @Test
    void nomDuCompteObligatoire() {
        Client c = new Client();
        c.setNumeroClient("C-0042");
        ValidationException ex = assertThrows(ValidationException.class, () -> service.creer(c));
        assertEquals("nomCompte", ex.getChamp());
    }

    @Test
    void emailInvalideRefuseAvecMessageClair() {
        Client c = clientValide();
        c.setEmailPrincipal("pas-un-email");
        ValidationException ex = assertThrows(ValidationException.class, () -> service.creer(c));
        assertEquals("emailPrincipal", ex.getChamp());
        assertTrue(ex.getMessage().contains("pas-un-email"),
                "Le message doit citer la valeur fautive pour aider l'utilisateur");
    }

    @Test
    void numeroClientEnDoublonRefuse() {
        Client existant = new Client();
        existant.setId(99L);
        clientsPortantCeNumero(Collections.singletonList(existant));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.creer(clientValide()));
        assertEquals("numeroClient", ex.getChamp());
        assertTrue(ex.getMessage().contains("déjà utilisé"));
    }

    @Test
    void modifierLeMemeCompteNEstPasUnDoublon() {
        Client existant = new Client();
        existant.setId(99L);
        existant.setNumeroClient("C-0042");
        clientsPortantCeNumero(Collections.singletonList(existant));
        when(em.find(Client.class, 99L)).thenReturn(existant);
        when(em.merge(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));
        when(referentielGeoService.assurer(anyString(), any())).thenAnswer(inv -> inv.getArgument(1));

        Client c = clientValide();
        c.setId(99L); // il modifie SA propre fiche : l'unicité ne doit pas le bloquer

        Client maj = assertDoesNotThrow(() -> service.modifier(c));
        assertEquals("PHARMACIE DU PLATEAU", maj.getNomCompte());
        verify(em).merge(any(Client.class));
    }

    @Test
    void modifierUnCompteInexistantMessageClair() {
        clientsPortantCeNumero(Collections.emptyList());
        when(em.find(eq(Client.class), any())).thenReturn(null);

        Client c = clientValide();
        c.setId(12345L);
        ValidationException ex = assertThrows(ValidationException.class, () -> service.modifier(c));
        assertEquals("id", ex.getChamp());
    }
}
