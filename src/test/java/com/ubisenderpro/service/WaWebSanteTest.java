package com.ubisenderpro.service;

import com.ubisenderpro.entity.WaWebSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Santé de réception d'une session WhatsApp Web : distincte du statut. Une
 * session « ouverte » peut recevoir illisible (DEGRADED = à reconnecter) ; hors
 * connexion, la santé est toujours ramenée à OK (pas de bannière fantôme) ; un
 * message entrant lisible lève l'état dégradé et horodate la réception.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WaWebSanteTest {

    @Mock
    private EntityManager em;
    @Mock
    private WaWebJournalService journal;

    @InjectMocks
    private WaWebSessionService service;

    private WaWebSession session(String statut, String sante) {
        WaWebSession s = new WaWebSession();
        s.setId(2L);
        s.setLibelle("acc-2");
        s.setStatut(statut);
        s.setSante(sante);
        when(em.find(WaWebSession.class, 2L)).thenReturn(s);
        when(em.merge(any(WaWebSession.class))).thenAnswer(inv -> inv.getArgument(0));
        return s;
    }

    @Test
    void sessionConnecteePasseEnDegrade() {
        WaWebSession s = session("CONNECTE", "OK");
        service.enregistrerEtat(2L, "CONNECTE", "DEGRADED", "Messages illisibles");
        assertEquals("DEGRADED", s.getSante());
        assertEquals("Messages illisibles", s.getSanteDetail());
    }

    @Test
    void santeIgnoreeHorsConnexion() {
        // Un DEGRADED reçu alors que la session n'est plus connectée ne doit PAS
        // laisser de bannière : la santé est ramenée à OK.
        WaWebSession s = session("DECONNECTE", "DEGRADED");
        service.enregistrerEtat(2L, "DECONNECTE", "DEGRADED", "peu importe");
        assertEquals("OK", s.getSante());
        assertNull(s.getSanteDetail());
    }

    @Test
    void deconnexionEffaceLEtatDegrade() {
        WaWebSession s = session("CONNECTE", "DEGRADED");
        service.enregistrerEtat(2L, "DECONNECTE", null, null);
        assertEquals("OK", s.getSante(), "Passer hors ligne efface l'alerte de réception");
    }

    @Test
    void messageEntrantLeveLeDegradeEtHorodate() {
        WaWebSession s = session("CONNECTE", "DEGRADED");
        assertNull(s.getDernierEntrantLe());
        service.marquerEntrant(2L);
        assertEquals("OK", s.getSante(), "Une réception lisible prouve que la session va bien");
        assertNotNull(s.getDernierEntrantLe(), "La dernière réception est horodatée");
    }

    @Test
    void enregistrerStatutSeulNeCasseRienSurLaSante() {
        WaWebSession s = session("CONNECTE", "OK");
        service.enregistrerStatut(2L, "CONNECTE");
        assertEquals("OK", s.getSante());
        assertEquals("CONNECTE", s.getStatut());
    }
}
