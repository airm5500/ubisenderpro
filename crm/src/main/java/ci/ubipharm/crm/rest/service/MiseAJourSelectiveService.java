package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.MiseAJourSelectiveDTO;
import ci.ubipharm.crm.rest.service.dto.ResultatMiseAJourDTO;

/** Change les memes champs sur plusieurs clients coches ; tout ou rien, chaque client modifie est journalise. */
public interface MiseAJourSelectiveService {

    ResultatMiseAJourDTO appliquer(MiseAJourSelectiveDTO demande);
}
