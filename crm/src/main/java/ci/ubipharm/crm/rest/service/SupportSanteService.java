package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.SupportSondeDTO;

/** Sondes de sante lancees a la demande : base, WhatsApp Web, e-mail, SMS, archivage, serveur. Ne leve jamais d'exception. */
public interface SupportSanteService {

    List<SupportSondeDTO> sonder();
}
