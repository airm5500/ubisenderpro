package ci.ubipharm.crm.rest.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.dto.AideTexteCleDTO;
import ci.ubipharm.crm.rest.service.dto.AideTexteDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;

class AideServiceImplTest {

    @Test
    void mapTupleToDtoRecopieChaqueColonne() {
        Tuple tuple = mock(Tuple.class);
        LocalDateTime le = LocalDateTime.of(2026, 10, 20, 9, 30);
        when(tuple.get("type", String.class)).thenReturn("BOUTON");
        when(tuple.get("cle", String.class)).thenReturn("factures|Importer");
        when(tuple.get("titre", String.class)).thenReturn("Importer des factures");
        when(tuple.get("texte", String.class)).thenReturn("Charge le fichier de la gestion commerciale.");
        when(tuple.get("modifiePar", String.class)).thenReturn("Administrateur");
        when(tuple.get("modifieLe", LocalDateTime.class)).thenReturn(le);

        AideTexteDTO texte = new AideServiceImpl(null, null, null, null).mapTupleToDto(tuple);

        assertThat(texte.getType()).isEqualTo("BOUTON");
        assertThat(texte.getCle()).isEqualTo("factures|Importer");
        assertThat(texte.getTitre()).isEqualTo("Importer des factures");
        assertThat(texte.getTexte()).isEqualTo("Charge le fichier de la gestion commerciale.");
        assertThat(texte.getModifiePar()).isEqualTo("Administrateur");
        assertThat(texte.getModifieLe()).isEqualTo(le);
    }

    @Test
    void unBooleenMariaDbSeLitQuelQueSoitSonType() {
        assertThat(AideServiceImpl.vrai(Boolean.TRUE)).isTrue();
        assertThat(AideServiceImpl.vrai(Boolean.FALSE)).isFalse();
        assertThat(AideServiceImpl.vrai((byte) 1)).isTrue();
        assertThat(AideServiceImpl.vrai(0)).isFalse();
        assertThat(AideServiceImpl.vrai(null)).isFalse();
    }

    @Test
    void retablirUnTexteDejaDOrigineNeLaissePasDeTraceAuJournal() {
        EntityManager em = mock(EntityManager.class);
        Query requete = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(requete);
        when(requete.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(requete);
        when(requete.executeUpdate()).thenReturn(0);
        JournalActionService journal = mock(JournalActionService.class);

        new AideServiceImpl(em, null, null, journal).reinitialiser(new AideTexteCleDTO("PAGE", "factures"));

        verify(journal, never()).enregistrer(anyString(), anyString(), org.mockito.ArgumentMatchers.any(), anyString());
    }
}
