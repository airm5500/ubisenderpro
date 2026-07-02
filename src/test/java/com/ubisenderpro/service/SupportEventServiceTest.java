package com.ubisenderpro.service;

import com.ubisenderpro.entity.ApplicationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Journal d'erreurs du Centre de support : dédoublonnage par signature
 * (une erreur répétée incrémente un compteur au lieu de remplir la base),
 * throttling anti-flood, troncature des champs, et garantie que la collecte
 * ne propage jamais d'erreur à l'opération métier appelante.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupportEventServiceTest {

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

    /**
     * Suffixe aléatoire en LETTRES uniquement : la signature normalise les
     * chiffres en '#', seules des lettres garantissent une signature inédite
     * (les compteurs de throttling sont statiques, partagés entre tests).
     */
    private static String alea() {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 14; i++) { sb.append((char) ('a' + r.nextInt(26))); }
        return sb.toString();
    }

    private void desactiverAutoTicket() {
        when(parametreService.valeur(eq("support.auto_ticket"), anyString())).thenReturn("false");
        when(parametreService.valeur(eq("support.retention_jours"), anyString())).thenReturn("90");
    }

    @SuppressWarnings("unchecked")
    private void evenementsEnBase(List<ApplicationEvent> resultat) {
        TypedQuery<ApplicationEvent> q = mock(TypedQuery.class);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.setMaxResults(anyInt())).thenReturn(q);
        when(q.getResultList()).thenReturn(resultat);
        when(em.createQuery(anyString(), eq(ApplicationEvent.class))).thenReturn(q);
    }

    @Test
    void signatureStableFaceAuxValeursVolatiles() {
        // Numéros de ligne, ids et espaces variables ne changent pas la signature.
        String s1 = SupportEventService.signature("JS", "Dashboard", "TypeError ligne 42 (id 12345)");
        String s2 = SupportEventService.signature("JS", "Dashboard", "TypeError ligne 97   (id 99999)");
        assertEquals(s1, s2, "Une même erreur avec des nombres volatils différents = même signature");
        assertNotEquals(s1, SupportEventService.signature("JS", "Campagnes", "TypeError ligne 42 (id 12345)"),
                "Le module fait partie de la signature");
        assertNotEquals(s1, SupportEventService.signature("EXCEPTION_JAVA", "Dashboard", "TypeError ligne 42 (id 12345)"),
                "Le type fait partie de la signature");
        assertEquals(24, s1.length(), "Hash court attendu (12 octets hexa)");
    }

    @Test
    void memeErreurIncrementeeAuLieuDEtreDupliquee() {
        desactiverAutoTicket();
        ApplicationEvent existant = new ApplicationEvent();
        existant.setOccurrences(3);
        evenementsEnBase(Collections.singletonList(existant));
        when(em.merge(any(ApplicationEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        ApplicationEvent e = service.collecter("JS", "Dashboard", "ERROR",
                "Erreur connue " + alea(), null, "hermann", null);

        assertNotNull(e);
        assertEquals(4, e.getOccurrences(), "La répétition incrémente le compteur");
        verify(em, never()).persist(any());
    }

    @Test
    void floodDUneMemeErreurThrottle() {
        desactiverAutoTicket();
        evenementsEnBase(Collections.emptyList());
        String message = "Boucle infinie " + alea();

        int acceptes = 0;
        for (int i = 0; i < 25; i++) {
            if (service.collecter("JS", "CRM", "ERROR", message, null, null, null) != null) { acceptes++; }
        }
        // Quota : 10 collectes/minute par signature. Même à cheval sur un
        // changement de minute, 25 appels ne peuvent pas dépasser 20 acceptés.
        assertTrue(acceptes >= 10, "Les premières collectes doivent passer : " + acceptes);
        assertTrue(acceptes <= 20, "Le flood doit être throttlé : " + acceptes + " acceptées sur 25");
    }

    @Test
    void messageVideIgnore() {
        assertNull(service.collecter("JS", "CRM", "ERROR", null, null, null, null));
        assertNull(service.collecter("JS", "CRM", "ERROR", "   ", null, null, null));
        verify(em, never()).persist(any());
    }

    @Test
    void laCollecteNePropageJamaisDErreur() {
        desactiverAutoTicket();
        when(em.createQuery(anyString(), eq(ApplicationEvent.class)))
                .thenThrow(new RuntimeException("base indisponible"));

        ApplicationEvent e = assertDoesNotThrow(() -> service.collecter(
                "EXCEPTION_JAVA", "Campagnes", "ERROR", "Panne " + alea(), null, null, null),
                "La capture d'un bug ne doit jamais faire échouer l'opération métier");
        assertNull(e, "En cas d'échec interne, la collecte renvoie simplement null");
    }

    @Test
    void champsTronquesAuxLimites() {
        desactiverAutoTicket();
        evenementsEnBase(Collections.emptyList());
        String tresLong = new String(new char[10000]).replace('\0', 'x');

        service.collecter("autre", tresLong, "warn", alea() + " " + tresLong, tresLong, tresLong, tresLong);

        ArgumentCaptor<ApplicationEvent> cap = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(em).persist(cap.capture());
        ApplicationEvent e = cap.getValue();
        assertEquals("AUTRE", e.getType(), "Type normalisé en majuscules");
        assertEquals("WARN", e.getNiveau(), "Niveau normalisé en majuscules");
        assertTrue(e.getMessageCourt().length() <= 500, "Message plafonné à 500");
        assertTrue(e.getModule().length() <= 50, "Module plafonné à 50");
        assertTrue(e.getPayloadJson().length() <= 4000, "Payload plafonné à 4000");
        assertTrue(e.getUrlOuEcran().length() <= 255, "URL plafonnée à 255");
    }
}
