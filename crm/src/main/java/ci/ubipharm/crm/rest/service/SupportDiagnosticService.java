package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.SupportErreurDTO;
import ci.ubipharm.crm.rest.service.dto.SupportErreurNavigateurDTO;

/**
 * Journal des erreurs inattendues, dedoublonnees par signature (type + message sans chiffres).
 * La capture est best-effort : elle ne leve jamais d'exception et s'enregistre dans sa propre transaction.
 */
public interface SupportDiagnosticService {

    /** Erreur du serveur (reponse 500) ; ne leve jamais d'exception. */
    void capturerServeur(Throwable erreur, String reference);

    /** Erreur JavaScript remontee par le navigateur ; ne leve jamais d'exception. */
    void capturerNavigateur(SupportErreurNavigateurDTO erreur);

    /** Erreurs a traiter, ou deja traitees, de la plus recente a la plus ancienne. */
    List<SupportErreurDTO> lister(boolean traitees);

    /** Marque l'erreur traitee ; elle redevient a traiter si elle survient de nouveau. */
    SupportErreurDTO traiter(Long id);

    /** Retire les erreurs dont la derniere survenue depasse support.retention_jours ; renvoie leur nombre. */
    int purger();
}
