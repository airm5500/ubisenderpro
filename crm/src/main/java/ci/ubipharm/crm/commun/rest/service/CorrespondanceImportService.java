package ci.ubipharm.crm.commun.rest.service;

import java.util.List;
import java.util.Map;

import ci.ubipharm.crm.commun.rest.service.dto.ChampImportDTO;
import ci.ubipharm.crm.commun.rest.service.dto.LigneTableurDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ModeleImportDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ParametresImportDTO;
import ci.ubipharm.crm.commun.rest.service.dto.TableurDTO;

/** Correspondance entre les colonnes d'un fichier et les champs de l'application, et ses modeles enregistres. */
public interface CorrespondanceImportService {

    /** Correspondance devinee a partir des intitules de colonne. */
    Map<String, String> proposer(List<ChampImportDTO> champs, List<String> entetes);

    /** Correspondance saisie, sinon celle du modele choisi, sinon celle devinee. */
    Map<String, String> resoudre(String type, List<ChampImportDTO> champs, List<String> entetes, ParametresImportDTO parametres);

    /** Libelles des champs obligatoires qu'aucune colonne du fichier ne remplit. */
    List<String> champsManquants(List<ChampImportDTO> champs, Map<String, String> correspondance, List<String> entetes);

    /** Lignes du fichier reecrites avec les codes des champs ; refuse une correspondance incomplete ou incoherente. */
    List<LigneTableurDTO> appliquer(List<ChampImportDTO> champs, TableurDTO tableur, Map<String, String> correspondance);

    List<ModeleImportDTO> modeles(String type);

    /** Cree le modele, ou le remplace s'il en existe deja un du meme nom pour ce type. */
    ModeleImportDTO enregistrerModele(String type, List<ChampImportDTO> champs, ModeleImportDTO modele);

    void archiverModele(Long id);
}
