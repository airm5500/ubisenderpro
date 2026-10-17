package ci.ubipharm.crm.securite.rest.service;

public interface JournalActionService {

    /** Trace une action metier de l'utilisateur connecte (qui, quoi, sur quoi, depuis quelle adresse). */
    void enregistrer(String action, String entite, Long entiteId, String details);

    /** Trace une action des traitements planifies, sans utilisateur connecte. */
    void enregistrerSysteme(String action, String entite, Long entiteId, String details);
}
