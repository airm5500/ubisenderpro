package ci.ubipharm.crm.rest.service;

public interface InitialisationAdminService {

    /** Hache le mot de passe initial du compte admin s'il ne l'est pas encore. */
    void initialiserMotDePasseAdmin();
}
