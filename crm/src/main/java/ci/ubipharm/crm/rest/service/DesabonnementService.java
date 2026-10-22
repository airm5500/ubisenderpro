package ci.ubipharm.crm.rest.service;

/** Desabonnement du Marketing : le client ne recoit plus de message Marketing ; les relances de recouvrement continuent. */
public interface DesabonnementService {

    void desabonner(Long clientId, String motif);

    void reabonner(Long clientId);

    /** Reponse WhatsApp recue : desabonne le client si le texte est un des mots parametres (STOP...). */
    boolean traiterReponse(Long clientId, String texte);
}
