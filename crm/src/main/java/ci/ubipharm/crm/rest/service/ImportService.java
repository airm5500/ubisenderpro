package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.ImportCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.ImportDTO;
import ci.ubipharm.crm.rest.service.dto.ImportLigneCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.ImportLigneDTO;
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

    /** Type de l'import (CLIENTS, FACTURES...), s'il est visible par l'utilisateur. */
    String type(Long id);

    /** Toutes les lignes d'une action donnee (REJET...), dans l'ordre du fichier. */
    List<ImportLigneDTO> lignesParAction(Long id, String action);

    /** Remplace le verdict et les donnees des lignes d'une simulation, puis recalcule son bilan. */
    ImportDTO majSimulation(Long id, List<ImportLigneDTO> lignes);

    /** Verrouille un import simule du type attendu et rend ses lignes avec leurs donnees d'origine. */
    List<ImportLigneDTO> lignesAValider(Long id, String type);

    /** Enregistre le resultat de chaque ligne (champ resultat) et clot l'import. */
    ImportDTO terminerValidation(Long id, List<ImportLigneDTO> lignes);
}
