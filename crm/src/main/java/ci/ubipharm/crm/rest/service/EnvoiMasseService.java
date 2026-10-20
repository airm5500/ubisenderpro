package ci.ubipharm.crm.rest.service;

import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.LectureDestinatairesDTO;
import ci.ubipharm.crm.rest.service.dto.PieceJointeDTO;
import ci.ubipharm.crm.rest.service.dto.ReferencesEnvoiMasseDTO;

import ci.ubipharm.crm.rest.service.dto.DestinataireMasseDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiMasseApercuDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiMasseDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiMasseDemandeDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;

/**
 * Envoi WhatsApp en masse : un meme message, personnalise par les variables du catalogue, avec ses pieces jointes, a des
 * numeros colles ou importes, a des clients choisis ou aux clients d'une liste de diffusion, depuis le numero WhatsApp Web de l'auteur. Les messages partent au debit des relances, apres elles ; les
 * desabonnes (STOP) et les clients desactives sont exclus, avec leur motif.
 */
public interface EnvoiMasseService {

    /** Avec apercu = true, compte seulement les destinataires retenus et exclus. */
    EnvoiMasseApercuDTO creer(EnvoiMasseDemandeDTO demande);

    /** Les envois de l'utilisateur ; tous pour une vision globale. */
    PageDTO<EnvoiMasseDTO> rechercher(int page, int taille);

    EnvoiMasseDTO consulter(Long id);

    PageDTO<DestinataireMasseDTO> destinataires(Long id, String statut, int page, int taille);

    /** Les messages pas encore partis sont annules ; ceux deja partis restent. */
    EnvoiMasseDTO annuler(Long id);

    /** Listes de diffusion, modeles WhatsApp actifs du Marketing et variables que l'envoi en masse sait remplir. */
    ReferencesEnvoiMasseDTO references();

    /** Lit un fichier CSV ou Excel de destinataires (numero, nom) sans l'enregistrer. */
    LectureDestinatairesDTO lireFichier(MultipartFile fichier);

    /** Piece jointe deposee pour un envoi a venir. */
    PieceJointeDTO deposerPieceJointe(MultipartFile fichier);

    /** Fichier joint a un envoi que l'utilisateur peut consulter. */
    FichierDTO pieceJointe(Long id, Long pieceId);

    /** Remet en attente les messages en echec d'un envoi (et le relance s'il etait termine). */
    EnvoiMasseDTO relancerEchecs(Long id);

    /** Un passage du planificateur : au plus un message par numero. */
    int envoyerLot();

    /** Au demarrage : un message reste en cours d'envoi apres un arret brutal passe en echec, jamais renvoye en double. */
    int reprendreEnvoisInterrompus();
}
