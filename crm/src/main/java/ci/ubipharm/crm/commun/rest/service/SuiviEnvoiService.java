package ci.ubipharm.crm.commun.rest.service;

/** Effets d'un envoi de relance, communs a WhatsApp et a l'e-mail : statut de la facture et journal de la campagne. */
public interface SuiviEnvoiService {

    /** Message parti : la facture passe a « relance envoyee » si elle n'etait pas plus avancee. */
    void factureRelancee(Long factureId);

    /** Message en echec : la facture redevient a relancer si aucun autre message ne doit l'envoyer. */
    void factureNonRelancee(Long factureId);

    /** Compteurs de la campagne, et fin de campagne quand plus rien n'attend. */
    void recompter(Long campagneId);
}
