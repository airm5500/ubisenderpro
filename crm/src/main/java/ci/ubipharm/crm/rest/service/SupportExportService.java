package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.FichierDTO;

/** Dossier de diagnostic a transmettre a l'editeur : un ZIP sans donnees de clients. */
public interface SupportExportService {

    /** info.txt (sondes de sante), erreurs.csv, tickets.csv, experience.csv (24 h) et la fin du journal du serveur. */
    FichierDTO exporter();
}
