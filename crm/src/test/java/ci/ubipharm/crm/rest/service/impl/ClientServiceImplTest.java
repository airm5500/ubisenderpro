package ci.ubipharm.crm.rest.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import ci.ubipharm.crm.rest.service.dto.ClientDTO;
import jakarta.persistence.Tuple;

class ClientServiceImplTest {

    private final ClientServiceImpl service = new ClientServiceImpl(null, null, null, null, null, null, null);

    @Test
    void mapTupleToDtoRecopieChaqueColonne() {
        Tuple tuple = mock(Tuple.class);
        when(tuple.get("id", Long.class)).thenReturn(7L);
        when(tuple.get("code", String.class)).thenReturn("PH007");
        when(tuple.get("raisonSociale", String.class)).thenReturn("Pharmacie des Lagunes");
        when(tuple.get("segmentation", String.class)).thenReturn("PLATINUM");
        when(tuple.get("ville", String.class)).thenReturn("Abidjan");
        when(tuple.get("statut", String.class)).thenReturn("ACTIF");
        when(tuple.get("agenceId", Long.class)).thenReturn(1L);
        when(tuple.get("agenceLibelle", String.class)).thenReturn("Yopougon");
        when(tuple.get("numeroPrincipal", String.class)).thenReturn("2250700000001");
        when(tuple.get("numeroSecondaire", String.class)).thenReturn(null);

        ClientDTO client = service.mapTupleToDto(tuple);

        assertThat(client.getId()).isEqualTo(7L);
        assertThat(client.getCode()).isEqualTo("PH007");
        assertThat(client.getRaisonSociale()).isEqualTo("Pharmacie des Lagunes");
        assertThat(client.getSegmentation()).isEqualTo("PLATINUM");
        assertThat(client.getAgenceLibelle()).isEqualTo("Yopougon");
        assertThat(client.getNumeroPrincipal()).isEqualTo("2250700000001");
        assertThat(client.getNumeroSecondaire()).isNull();
    }
}
