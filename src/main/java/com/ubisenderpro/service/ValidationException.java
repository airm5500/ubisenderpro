package com.ubisenderpro.service;

import javax.ejb.ApplicationException;

/**
 * Erreur de validation métier porteuse d'un message clair (lisible par un
 * utilisateur non informaticien) et, le cas échéant, du nom du champ fautif.
 *
 * <p>{@code @ApplicationException(rollback = true)} évite l'enveloppe EJBException :
 * l'exception remonte telle quelle jusqu'au mapper JAX-RS.</p>
 */
@ApplicationException(rollback = true)
public class ValidationException extends RuntimeException {

    private final String champ;

    public ValidationException(String champ, String message) {
        super(message);
        this.champ = champ;
    }

    public ValidationException(String message) {
        this(null, message);
    }

    public String getChamp() {
        return champ;
    }
}
