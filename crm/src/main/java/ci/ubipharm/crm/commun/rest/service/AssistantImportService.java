package ci.ubipharm.crm.commun.rest.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.commun.rest.service.dto.ApercuImportDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ChampImportDTO;
import ci.ubipharm.crm.commun.rest.service.dto.CorrectionLigneDTO;
import ci.ubipharm.crm.commun.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ImportDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ModeleImportDTO;

/**
 * Assistant commun a tous les imports : champs attendus, exemplaire a remplir, apercu des colonnes
 * avec correspondance proposee, modeles de correspondance, correction de lignes et fichier des rejets.
 */
public interface AssistantImportService {

    List<ChampImportDTO> champs(String type);

    FichierDTO exemplaire(String type);

    ApercuImportDTO apercu(String type, MultipartFile fichier, Long modeleId);

    List<ModeleImportDTO> modeles(String type);

    ModeleImportDTO enregistrerModele(String type, ModeleImportDTO modele);

    void archiverModele(Long id);

    ImportDTO corriger(Long importId, Long ligneId, CorrectionLigneDTO correction);

    /** Lignes rejetees avec leur motif, en CSV a corriger dans Excel puis a reimporter. */
    FichierDTO rejets(Long importId);
}
