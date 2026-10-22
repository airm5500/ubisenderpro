package ci.ubipharm.crm.rest.service;

import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.LectureModeleWordDTO;

/** Modeles de messages au format Word (.docx), dans les deux sens. */
public interface ModeleWordService {

    /** Le modele dans un .docx que l'on peut retoucher puis reimporter. */
    FichierDTO exporter(String module, Long id);

    /**
     * Lit un .docx exporte par le CRM (etiquettes et texte entre reperes) ou un document Word quelconque
     * (tout son texte devient le message). Les champs de publipostage «champ» deviennent des variables {champ}.
     */
    LectureModeleWordDTO lire(String module, MultipartFile fichier);
}
