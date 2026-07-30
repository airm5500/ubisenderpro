package com.ubisenderpro.service;

import com.ubisenderpro.entity.Campagne;
import com.ubisenderpro.entity.CampagneDestinataire;
import com.ubisenderpro.entity.ClientContact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Ciblage d'une campagne par PLUSIEURS segmentations (champ CSV
 * « segmentationIds » alimenté par le sélecteur multiple de l'assistant).
 * Contrat verrouillé : chaque identifiant du CSV doit donner lieu à une
 * recherche de contacts, et les doublons entre segmentations sont exclus.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CampagneSegmentationsTest {

    @Mock
    private EntityManager em;
    @Mock
    private SegmentService segmentService;
    @Mock
    private ListeService listeService;
    @Mock
    private Query deleteQuery;

    @InjectMocks
    private CampagneService service;

    private ClientContact contact(Long id, String numero) {
        ClientContact c = new ClientContact();
        c.setId(id);
        c.setNumeroWhatsapp(numero);
        c.setNomComplet("Contact " + id);
        return c;
    }

    /**
     * Prépare l'EntityManager : la requête de purge, puis une requête de contacts
     * par segmentation qui renvoie les contacts associés au paramètre « seg ».
     * Renvoie la liste des identifiants de segmentation réellement interrogés.
     */
    @SuppressWarnings("unchecked")
    private List<Object> preparer(Campagne campagne, java.util.Map<Long, List<ClientContact>> parSegmentation) {
        when(em.find(eq(Campagne.class), any())).thenReturn(campagne);
        when(em.createQuery(anyString())).thenReturn(deleteQuery);
        when(deleteQuery.setParameter(anyString(), any())).thenReturn(deleteQuery);
        when(deleteQuery.executeUpdate()).thenReturn(0);

        List<Object> segmentationsInterrogees = new ArrayList<>();
        TypedQuery<ClientContact> q = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(ClientContact.class))).thenReturn(q);
        when(q.setParameter(eq("seg"), any())).thenAnswer(inv -> {
            Object valeur = inv.getArgument(1);
            segmentationsInterrogees.add(valeur);
            when(q.getResultList()).thenReturn(
                    parSegmentation.getOrDefault(valeur, Collections.emptyList()));
            return q;
        });
        return segmentationsInterrogees;
    }

    @Test
    void chaqueSegmentationDuCsvEstInterrogee() {
        Campagne c = new Campagne();
        c.setId(7L);
        c.setSegmentationIds("3, 5 ,8"); // espaces volontaires : le CSV vient de l'interface

        java.util.Map<Long, List<ClientContact>> data = new java.util.HashMap<>();
        data.put(3L, Collections.singletonList(contact(1L, "22501010101")));
        data.put(5L, Collections.singletonList(contact(2L, "22502020202")));
        data.put(8L, Collections.singletonList(contact(3L, "22503030303")));

        List<Object> interrogees = preparer(c, data);
        int total = service.construireDestinataires(7L);

        assertEquals(Arrays.asList(3L, 5L, 8L), interrogees,
                "Les trois segmentations du CSV doivent etre interrogees");
        assertEquals(3, total, "Un destinataire par segmentation");
        assertEquals(3, c.getNbDestinataires());
    }

    @Test
    void doublonEntreSegmentationsCompteUneSeuleFois() {
        Campagne c = new Campagne();
        c.setId(7L);
        c.setSegmentationIds("3,5");

        // Le meme numero present dans les deux segmentations.
        java.util.Map<Long, List<ClientContact>> data = new java.util.HashMap<>();
        data.put(3L, Collections.singletonList(contact(1L, "22501010101")));
        data.put(5L, Collections.singletonList(contact(2L, "22501010101")));

        preparer(c, data);
        int total = service.construireDestinataires(7L);

        assertEquals(1, total, "Le meme numero ne doit etre cible qu'une fois");
    }

    @Test
    void csvInvalideNeFaitPasEchouerLaConstruction() {
        Campagne c = new Campagne();
        c.setId(7L);
        c.setSegmentationIds("3,abc,,5"); // valeurs parasites

        java.util.Map<Long, List<ClientContact>> data = new java.util.HashMap<>();
        data.put(3L, Collections.singletonList(contact(1L, "22501010101")));
        data.put(5L, Collections.singletonList(contact(2L, "22502020202")));

        List<Object> interrogees = assertDoesNotThrow(() -> {
            List<Object> l = preparer(c, data);
            service.construireDestinataires(7L);
            return l;
        });
        assertEquals(Arrays.asList(3L, 5L), interrogees,
                "Seuls les identifiants valides sont interroges");
    }

    @Test
    void contactDesabonneEstMarqueEtNonEnvoyable() {
        Campagne c = new Campagne();
        c.setId(7L);
        c.setSegmentationIds("3");

        ClientContact desabonne = contact(1L, "22501010101");
        desabonne.setDesabonne(true);
        java.util.Map<Long, List<ClientContact>> data = new java.util.HashMap<>();
        data.put(3L, Collections.singletonList(desabonne));

        preparer(c, data);
        service.construireDestinataires(7L);

        ArgumentCaptor<CampagneDestinataire> cap = ArgumentCaptor.forClass(CampagneDestinataire.class);
        verify(em).persist(cap.capture());
        assertEquals("DESABONNE", cap.getValue().getStatut(),
                "Un desabonne est trace mais ne doit pas partir en EN_ATTENTE");
    }

    @Test
    void sansAucunCiblageAucunDestinataire() {
        Campagne c = new Campagne();
        c.setId(7L);
        // Ni segmentationIds, ni liste, ni segment : c'est le cas qui se produisait
        // quand l'assistant n'transmettait pas la selection multiple.
        preparer(c, Collections.emptyMap());

        assertEquals(0, service.construireDestinataires(7L));
        verify(em, never()).persist(any(CampagneDestinataire.class));
    }
}
