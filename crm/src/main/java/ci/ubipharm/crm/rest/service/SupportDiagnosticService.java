package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.SupportErreurDTO;
import ci.ubipharm.crm.rest.service.dto.SupportErreurNavigateurDTO;
import ci.ubipharm.crm.rest.service.dto.SupportTicketDTO;

/**
 * Journal des erreurs inattendues, dedoublonnees par signature (type + message sans chiffres).
 * La capture est best-effort : elle ne leve jamais d'exception et s'enregistre dans sa propre transaction.
 */
public interface SupportDiagnosticService {

    /** Erreur du serveur (reponse 500) ; ne leve jamais d'exception. */
    void capturerServeur(Throwable erreur, String reference);

    /** Erreur JavaScript remontee par le navigateur ; ne leve jamais d'exception. */
    void capturerNavigateur(SupportErreurNavigateurDTO erreur);

    /**
     * Requete lente (LENTEUR) ou alerte de la surveillance du serveur (SURVEILLANCE), hors de toute requete :
     * le chemin et l'utilisateur sont fournis par l'appelant ; ne leve jamais d'exception.
     */
    void signaler(String origine, String type, String message, String detail, String chemin, Long utilisateurId);

    /** Erreurs a traiter, ou deja traitees, de la plus recente a la plus ancienne ; origine vide = toutes. */
    List<SupportErreurDTO> lister(boolean traitees, String origine);

    SupportErreurDTO consulter(Long id);

    /** Ouvre a la main le ticket d'une erreur qui n'en a pas (ou dont le ticket est ferme) ; renvoie le ticket. */
    SupportTicketDTO creerTicket(Long id);

    /** Marque l'erreur traitee ; elle redevient a traiter si elle survient de nouveau. */
    SupportErreurDTO traiter(Long id);

    /** Retire les erreurs dont la derniere survenue depasse support.retention_jours ; renvoie leur nombre. */
    int purger();
}
