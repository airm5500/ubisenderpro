package ci.ubipharm.crm.rest.service;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

/** Copie des documents recus ou produits dans le repertoire d'archivage parametre ; ne bloque jamais l'action metier. */
public interface ArchivageService {

    /** Range le document sous son type (imports-clients, rejets, affiches...) selon le classement choisi. */
    void archiver(String type, String nomFichier, byte[] contenu);

    default void archiver(String type, MultipartFile fichier) {
        try {
            archiver(type, fichier.getOriginalFilename(), fichier.getBytes());
        } catch (IOException e) {
            archiver(type, fichier.getOriginalFilename(), null);
        }
    }
}
