package ci.ubipharm.crm.rest.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.PieceJointeDTO;

/** Pieces jointes des campagnes : affiche choisie par l'auteur, liste Excel des produits generee. Jamais supprimees. */
public interface PieceJointeService {

    /** Affiche (JPEG, PNG ou PDF) deposee par l'auteur d'une campagne. */
    PieceJointeDTO deposerAffiche(MultipartFile fichier);

    PieceJointeDTO decrire(Long id);

    FichierDTO lire(Long id);

    /** Enregistre un fichier produit par le CRM (liste des produits) ; utilisable hors session. */
    Long enregistrerGenere(String nature, FichierDTO fichier);

    /** Fichiers a joindre aux messages d'une campagne, dans l'ordre d'envoi : l'affiche puis la liste des produits. */
    List<FichierDTO> piecesDeCampagne(Long campagneId);
}
