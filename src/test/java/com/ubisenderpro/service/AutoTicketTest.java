package com.ubisenderpro.service;

import com.ubisenderpro.entity.ApplicationEvent;
import com.ubisenderpro.entity.SupportTicket;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Anticipation des bugs : une erreur inattendue jamais vue (nouvelle signature)
 * ouvre automatiquement un ticket BUG ; les erreurs SQL (souvent liées à la
 * saisie) et les répétitions n'en ouvrent pas.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutoTicketTest {

    @Mock
    private EntityManager em;
    @Mock
    private ParametreService parametreService;
    @Mock
    private SupportService supportService;
    @Mock
    private MailService mailService;

    @InjectMocks
    private SupportEventService service;

    @SuppressWarnings("unchecked")
    private void aucunEvenementExistant() {
        TypedQuery<ApplicationEvent> q = mock(TypedQuery.class);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.setMaxResults(anyInt())).thenReturn(q);
        when(q.getResultList()).thenReturn(Collections.emptyList());
        when(em.createQuery(anyString(), eq(ApplicationEvent.class))).thenReturn(q);
    }

    private void parametresParDefaut() {
        when(parametreService.valeur(eq("support.auto_ticket"), anyString())).thenReturn("true");
        when(parametreService.valeur(eq("support.auto_ticket_max_jour"), anyString())).thenReturn("10");
        when(parametreService.valeur(eq("support.retention_jours"), anyString())).thenReturn("90");
        when(parametreService.valeur(eq("support.email"), anyString())).thenReturn("editeur@test.ci");
    }

    private SupportTicket ticketCree() {
        SupportTicket t = new SupportTicket();
        t.setId(42L);
        t.setNumero("TCK-2026-0042");
        t.setPriorite("HAUTE");
        return t;
    }

    @Test
    void nouvelleExceptionJavaOuvreUnTicketEtNotifie() {
        aucunEvenementExistant();
        parametresParDefaut();
        when(supportService.creerTicket(any(SupportTicket.class), eq("systeme"))).thenReturn(ticketCree());

        ApplicationEvent e = service.collecter("EXCEPTION_JAVA", "Campagnes", "ERROR",
                "NullPointerException lors du lancement " + System.nanoTime(), "stack…", null, "/campaigns");

        assertNotNull(e, "L'événement doit être collecté");
        assertEquals(Long.valueOf(42L), e.getTicketId(), "L'événement doit être lié au ticket auto");
        verify(supportService).creerTicket(argThat(t ->
                "BUG".equals(t.getType()) && "HAUTE".equals(t.getPriorite())
                        && t.getSujet().startsWith("[AUTO]")), eq("systeme"));
        verify(mailService).envoyerAvecPieces(eq(Collections.singletonList("editeur@test.ci")),
                contains("TCK-2026-0042"), anyString(), isNull());
    }

    @Test
    void erreurSqlResteAuJournalSansTicket() {
        aucunEvenementExistant();
        parametresParDefaut();

        ApplicationEvent e = service.collecter("SQL", "Comptes clients", "ERROR",
                "Duplicate entry 'C001' " + System.nanoTime(), null, null, "/clients");

        assertNotNull(e);
        verify(supportService, never()).creerTicket(any(), any());
        verify(mailService, never()).envoyerAvecPieces(any(), any(), any(), any());
    }

    @Test
    void desactiveParParametreAucunTicket() {
        aucunEvenementExistant();
        parametresParDefaut();
        when(parametreService.valeur(eq("support.auto_ticket"), anyString())).thenReturn("false");

        service.collecter("EXCEPTION_JAVA", "CRM", "ERROR",
                "Erreur inattendue " + System.nanoTime(), null, null, "/crm");

        verify(supportService, never()).creerTicket(any(), any());
    }

    @Test
    void echecDuTicketNeBloqueJamaisLaCollecte() {
        aucunEvenementExistant();
        parametresParDefaut();
        when(supportService.creerTicket(any(), any())).thenThrow(new RuntimeException("boom"));

        ApplicationEvent e = assertDoesNotThrow(() ->
                service.collecter("JS", "Dashboard", "ERROR",
                        "TypeError x is undefined " + System.nanoTime(), null, null, "app.js:12"));
        assertNotNull(e, "La collecte doit aboutir même si l'auto-ticket échoue");
        assertNull(e.getTicketId());
    }
}
