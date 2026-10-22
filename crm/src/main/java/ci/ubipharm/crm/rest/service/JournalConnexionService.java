package ci.ubipharm.crm.rest.service;

public interface JournalConnexionService {

    void enregistrerSucces(Long utilisateurId, String login, String adresseIp, String navigateur);

    /** Trace l'echec et verrouille le compte apres le nombre d'echecs parametre. */
    void enregistrerEchec(String login, String adresseIp, String navigateur);
}
