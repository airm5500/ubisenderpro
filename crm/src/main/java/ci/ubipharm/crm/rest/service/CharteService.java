package ci.ubipharm.crm.rest.service;

import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.dto.CharteDTO;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;

public interface CharteService {

    CharteDTO charte();

    /** LOGO ou ICONE en vigueur, ou null pour celui livre avec l'application. */
    FichierDTO fichier(String code);

    /** Remplace le logo ou l'icone ; l'ancien depot reste en base, desactive. */
    CharteDTO deposer(String code, MultipartFile fichier);

    /** Revient au logo ou a l'icone livre avec l'application. */
    CharteDTO reinitialiser(String code);
}
