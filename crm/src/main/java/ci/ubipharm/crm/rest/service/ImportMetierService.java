package ci.ubipharm.crm.rest.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.dto.ChampImportDTO;
import ci.ubipharm.crm.rest.service.dto.CorrectionLigneDTO;
import ci.ubipharm.crm.rest.service.dto.ImportDTO;
import ci.ubipharm.crm.rest.service.dto.ParametresImportDTO;
import ci.ubipharm.crm.rest.service.dto.ValidationImportDTO;

/** Ce que chaque type d'import fournit a l'assistant : ses champs, sa simulation, sa validation, ses corrections. */
public interface ImportMetierService {

    /** CLIENTS, FACTURES... */
    String type();

    /** Module exige en plus du role pour utiliser cet import, ou null. */
    String moduleRequis();

    List<ChampImportDTO> champs();

    /** Lit le fichier, applique la correspondance de colonnes et evalue chaque ligne sans rien modifier. */
    ImportDTO simuler(MultipartFile fichier, ParametresImportDTO parametres);

    /** Applique les creations et les mises a jour choisies, apres re-evaluation contre l'etat courant. */
    ImportDTO valider(Long id, ValidationImportDTO choix);

    /** Remplace les valeurs d'une ligne de la simulation puis re-evalue toutes les lignes. */
    ImportDTO corriger(Long id, Long ligneId, CorrectionLigneDTO correction);
}
