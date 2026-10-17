package ci.ubipharm.crm.securite.rest.service;

public interface JournalActionService {

    /** Trace une action metier de l'utilisateur connecte (qui, quoi, sur quoi, depuis quelle adresse). */
    void enregistrer(String action, String entite, Long entiteId, String details);
}
