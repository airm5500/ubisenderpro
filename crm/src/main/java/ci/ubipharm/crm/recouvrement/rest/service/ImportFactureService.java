package ci.ubipharm.crm.recouvrement.rest.service;

import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.commun.rest.service.dto.ImportDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ValidationImportDTO;

public interface ImportFactureService {

    /** Lit le fichier et evalue chaque facture sans rien modifier. */
    ImportDTO simuler(MultipartFile fichier);

    /** Applique les creations et les mises a jour choisies, apres re-evaluation contre l'etat courant. */
    ImportDTO valider(Long id, ValidationImportDTO choix);
}
