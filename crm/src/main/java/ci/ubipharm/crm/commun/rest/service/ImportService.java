package ci.ubipharm.crm.commun.rest.service;

import java.util.List;

import ci.ubipharm.crm.commun.rest.service.dto.ImportCriteresDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ImportDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ImportLigneCriteresDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ImportLigneDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;

/**
 * Cycle de vie commun a tous les imports : simulation enregistree ligne par ligne,
 * consultation, annulation, puis validation qui consigne le resultat de chaque ligne.
 */
public interface ImportService {

    ImportDTO simulation(String type, String nomFichier, List<ImportLigneDTO> lignes);

    PageDTO<ImportDTO> historique(ImportCriteresDTO criteres);

    ImportDTO consulter(Long id);

    PageDTO<ImportLigneDTO> lignes(Long id, ImportLigneCriteresDTO criteres);

    ImportDTO annuler(Long id);

    /** Verrouille un import simule du type attendu et rend ses lignes avec leurs donnees d'origine. */
    List<ImportLigneDTO> lignesAValider(Long id, String type);

    /** Enregistre le resultat de chaque ligne (champ resultat) et clot l'import. */
    ImportDTO terminerValidation(Long id, List<ImportLigneDTO> lignes);
}
