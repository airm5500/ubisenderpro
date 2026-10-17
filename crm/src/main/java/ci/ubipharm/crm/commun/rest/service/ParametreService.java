package ci.ubipharm.crm.commun.rest.service;

public interface ParametreService {

    /** Valeur du parametre, ou la valeur par defaut s'il est absent ou vide. */
    String valeur(String cle, String parDefaut);

    int entier(String cle, int parDefaut);
}
