package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.JournalCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.JournalLigneDTO;
import ci.ubipharm.crm.rest.service.dto.JournalReferentielDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;

/** Journal des actions de toute l'application (creations, modifications, desactivations, connexions...) : qui a fait quoi. */
public interface JournalService {

    PageDTO<JournalLigneDTO> rechercher(JournalCriteresDTO criteres);

    JournalReferentielDTO referentiel();

    /** CSV lisible par Excel, memes filtres que l'ecran (50 000 lignes au plus). */
    FichierDTO exporter(JournalCriteresDTO criteres);

    /** Classeur Excel (.xlsx), memes filtres que l'ecran (50 000 lignes au plus). */
    FichierDTO exporterExcel(JournalCriteresDTO criteres);
}
