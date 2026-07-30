package com.ubisenderpro.service;

import com.ubisenderpro.entity.SupportDemande;
import com.ubisenderpro.entity.SupportTicket;
import com.ubisenderpro.entity.SupportTicketMessage;
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
import java.time.Year;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Workflow des tickets de support (numérotation annuelle, statuts, traçabilité
 * dans la conversation) et demandes « Me contacter » (archivage + e-mails).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupportServiceTest {

    @Mock
    private EntityManager em;
    @Mock
    private MailService mailService;
    @Mock
    private MediaFichierService mediaFichierService;
    @Mock
    private ParametreService parametreService;

    @InjectMocks
    private SupportService service;

    @SuppressWarnings("unchecked")
    private void ticketsDejaEmisCetteAnnee(long nombre) {
        TypedQuery<Long> q = mock(TypedQuery.class);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(nombre);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(q);
    }

    /* ------------------------------- Tickets ------------------------------- */

    @Test
    void numeroDeTicketSequentielParAnnee() {
        ticketsDejaEmisCetteAnnee(41L);
        SupportTicket t = new SupportTicket();
        t.setSujet("Impossible de lancer une campagne");

        SupportTicket cree = service.creerTicket(t, "hermann");

        assertEquals("TCK-" + Year.now().getValue() + "-0042", cree.getNumero());
        assertEquals("NOUVEAU", cree.getStatut(), "Un ticket démarre toujours au statut NOUVEAU");
        assertEquals("hermann", cree.getUtilisateur());
    }

    @Test
    void sujetObligatoire() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.creerTicket(new SupportTicket(), "hermann"));
        assertEquals("sujet", ex.getChamp());
    }

    @Test
    void descriptionDevientPremierMessageDeLaConversation() {
        ticketsDejaEmisCetteAnnee(0L);
        SupportTicket t = new SupportTicket();
        t.setSujet("Bug affichage");
        t.setDescription("Le tableau ne se rafraichit pas.");

        service.creerTicket(t, "awa");

        ArgumentCaptor<Object> cap = ArgumentCaptor.forClass(Object.class);
        verify(em, times(2)).persist(cap.capture()); // le ticket puis le message
        SupportTicketMessage m = cap.getAllValues().stream()
                .filter(o -> o instanceof SupportTicketMessage)
                .map(o -> (SupportTicketMessage) o)
                .findFirst().orElse(null);
        assertNotNull(m, "La description doit devenir le premier message");
        assertEquals("CLIENT", m.getDirection());
        assertEquals("awa", m.getAuteur());
        assertEquals("Le tableau ne se rafraichit pas.", m.getCorps());
    }

    @Test
    void statutInconnuRefuse() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.changerStatut(1L, "N_IMPORTE_QUOI", "admin"));
        assertEquals("statut", ex.getChamp());
    }

    @Test
    void changementDeStatutTraceDansLaConversation() {
        SupportTicket t = new SupportTicket();
        t.setId(7L);
        t.setStatut("NOUVEAU");
        when(em.find(SupportTicket.class, 7L)).thenReturn(t);
        when(em.merge(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));

        SupportTicket maj = service.changerStatut(7L, "en_cours", "support");

        assertEquals("EN_COURS", maj.getStatut(), "Statut normalisé en majuscules");
        ArgumentCaptor<SupportTicketMessage> cap = ArgumentCaptor.forClass(SupportTicketMessage.class);
        verify(em).persist(cap.capture());
        assertEquals("SYSTEME", cap.getValue().getDirection(), "Le changement est tracé en ligne système");
        assertTrue(cap.getValue().getCorps().contains("NOUVEAU"), "L'ancien statut est tracé");
        assertTrue(cap.getValue().getCorps().contains("EN_COURS"), "Le nouveau statut est tracé");
    }

    @Test
    void affectationPasseLeTicketEnAffecte() {
        SupportTicket t = new SupportTicket();
        t.setId(9L);
        t.setStatut("NOUVEAU");
        when(em.find(SupportTicket.class, 9L)).thenReturn(t);
        when(em.merge(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));

        SupportTicket maj = service.affecter(9L, "support", "support");

        assertEquals("support", maj.getAffecteA());
        assertEquals("AFFECTE", maj.getStatut(), "NOUVEAU/OUVERT passe en AFFECTE lors de l'affectation");
    }

    @Test
    void ticketIntrouvableMessageClair() {
        when(em.find(eq(SupportTicket.class), any())).thenReturn(null);
        assertThrows(ValidationException.class, () -> service.changerStatut(999L, "OUVERT", "admin"));
        assertThrows(ValidationException.class, () -> service.affecter(999L, "support", "admin"));
    }

    /* ----------------------- Demandes « Me contacter » ----------------------- */

    @Test
    void demandeSansObjetOuSansCorpsRefusee() {
        SupportDemande sansObjet = new SupportDemande();
        sansObjet.setCorps("Bonjour");
        assertEquals("objet", assertThrows(ValidationException.class,
                () -> service.creerDemande(sansObjet)).getChamp());

        SupportDemande sansCorps = new SupportDemande();
        sansCorps.setObjet("Question");
        assertEquals("corps", assertThrows(ValidationException.class,
                () -> service.creerDemande(sansCorps)).getChamp());
    }

    @Test
    void demandeArchiveeSiEmailSupportAbsent() {
        when(parametreService.valeur(eq("support.email"), anyString())).thenReturn("");
        SupportDemande d = new SupportDemande();
        d.setObjet("Question sur les licences");
        d.setCorps("Comment renouveler ?");

        SupportDemande out = service.creerDemande(d);

        assertEquals("ARCHIVEE", out.getStatut(), "Sans destinataire, la demande est archivée");
        assertNotNull(out.getErreur(), "La raison de l'archivage est expliquée");
        verify(mailService, never()).envoyerAvecPieces(any(), any(), any(), any());
        verify(mailService, never()).envoyer(any(), any(), any());
    }

    @Test
    void demandeEnvoyeeAvecAccuseDeReception() {
        when(parametreService.valeur(eq("support.email"), anyString())).thenReturn("editeur@ubipharm.ci");
        when(mailService.estConfigure()).thenReturn(true);
        SupportDemande d = new SupportDemande();
        d.setObjet("Probleme d'envoi");
        d.setCorps("Les campagnes restent en attente.");
        d.setNom("Hermann NZI");
        d.setEmail("pharma@client.ci");

        SupportDemande out = service.creerDemande(d);

        assertEquals("ENVOYEE", out.getStatut());
        verify(mailService).envoyerAvecPieces(eq(Collections.singletonList("editeur@ubipharm.ci")),
                contains("Probleme d'envoi"), contains("Les campagnes restent en attente."), anyList());
        verify(mailService).envoyer(eq(Collections.singletonList("pharma@client.ci")),
                anyString(), contains("Probleme d'envoi"));
    }
}
