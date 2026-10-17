package ci.ubipharm.crm.commun.rest.service;

import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.commun.rest.service.dto.ImportDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ValidationImportDTO;

public interface ImportClientService {

    /** Lit le fichier et evalue chaque ligne sans rien modifier dans la base clients. */
    ImportDTO simuler(MultipartFile fichier);

    /** Applique les creations et les mises a jour choisies, apres re-evaluation contre l'etat courant. */
    ImportDTO valider(Long id, ValidationImportDTO choix);
}
