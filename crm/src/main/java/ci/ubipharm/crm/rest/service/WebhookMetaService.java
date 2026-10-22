package ci.ubipharm.crm.rest.service;

/** Notifications de Meta : accuses des messages envoyes et reponses des clients sur les numeros de l'API officielle. */
public interface WebhookMetaService {

    /** Abonnement du webhook : renvoie le defi si le jeton de verification est le bon, sinon null. */
    String verifierAbonnement(String mode, String jeton, String defi);

    /** Refuse un corps dont la signature X-Hub-Signature-256 ne correspond pas au secret de l'application. */
    void verifierSignature(String corps, String signature);

    void traiter(String corps);
}
