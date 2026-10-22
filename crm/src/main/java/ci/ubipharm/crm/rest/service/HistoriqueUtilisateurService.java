package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.ActionJournalDTO;
import ci.ubipharm.crm.rest.service.dto.ConnexionHistoriqueDTO;
import ci.ubipharm.crm.rest.service.dto.HistoriqueCriteresDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;

/** Historique d'un compte, pour l'administrateur : connexions (reussies et echouees) et journal de ses actions. */
public interface HistoriqueUtilisateurService {

    PageDTO<ConnexionHistoriqueDTO> connexions(Long utilisateurId, HistoriqueCriteresDTO criteres);

    PageDTO<ActionJournalDTO> actions(Long utilisateurId, HistoriqueCriteresDTO criteres);
}
