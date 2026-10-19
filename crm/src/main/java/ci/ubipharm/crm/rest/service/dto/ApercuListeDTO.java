package ci.ubipharm.crm.rest.service.dto;

import ci.ubipharm.crm.socle.dto.PageDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApercuListeDTO {

    private long nbClients;
    private long nbJoignables;
    private long nbDesabonnes;
    private long nbSansCoordonnees;
    private PageDTO<MembreListeDTO> membres;
}
