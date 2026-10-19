package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.EnvoiHistoriqueCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiHistoriqueDTO;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;

/**
 * Historique complet des envois : relances et promotions (tous canaux), messages ecrits dans les discussions, envois en masse.
 * Un agent voit ses envois et ceux des agents qu'il remplace ; la direction, ceux de ses agences.
 */
public interface HistoriqueEnvoiService {

    PageDTO<EnvoiHistoriqueDTO> rechercher(EnvoiHistoriqueCriteresDTO criteres);

    /** Les memes lignes en CSV (separateur ;), plafonnees a 50 000. */
    FichierDTO exporter(EnvoiHistoriqueCriteresDTO criteres);
}
