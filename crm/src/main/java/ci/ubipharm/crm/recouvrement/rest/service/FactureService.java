package ci.ubipharm.crm.recouvrement.rest.service;

import java.util.List;

import ci.ubipharm.crm.recouvrement.rest.service.dto.FactureCriteresDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.FactureDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.SousTypeDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;

public interface FactureService {

    PageDTO<FactureDTO> rechercher(FactureCriteresDTO criteres);

    List<SousTypeDTO> sousTypes();
}
