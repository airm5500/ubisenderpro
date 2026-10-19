package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.DestinataireMasseDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiMasseApercuDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiMasseDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiMasseDemandeDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;

/**
 * Envoi WhatsApp en masse : un meme message, personnalise par {nom}, a des numeros colles ou aux clients d'une liste de
 * diffusion, depuis le numero WhatsApp Web de l'auteur. Les messages partent au debit des relances, apres elles ; les
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

    /** Un passage du planificateur : au plus un message par numero. */
    int envoyerLot();

    /** Au demarrage : un message reste en cours d'envoi apres un arret brutal passe en echec, jamais renvoye en double. */
    int reprendreEnvoisInterrompus();
}
