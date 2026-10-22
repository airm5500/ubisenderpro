package ci.ubipharm.crm.socle.erreur;

/**
 * Erreur fonctionnelle dont le message peut etre montre a l'utilisateur.
 * Toute autre exception est masquee derriere un message generique.
 */
public class ErreurMetierException extends RuntimeException {

    public ErreurMetierException(String message) {
        super(message);
    }
}
