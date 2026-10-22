package ci.ubipharm.crm.rest.service;

import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.dto.TableurDTO;

public interface LecteurTableurService {

    /** Lit un fichier .xlsx, .xls ou .csv : premiere ligne = en-tetes, lignes vides ignorees. */
    TableurDTO lire(MultipartFile fichier);
}
