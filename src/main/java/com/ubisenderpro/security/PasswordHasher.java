package com.ubisenderpro.security;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Hachage et vérification des mots de passe avec BCrypt.
 */
public final class PasswordHasher {

    private PasswordHasher() {
    }

    public static String hash(String motDePasse) {
        return BCrypt.hashpw(motDePasse, BCrypt.gensalt(10));
    }

    public static boolean verify(String motDePasse, String hash) {
        if (motDePasse == null || hash == null || !hash.startsWith("$2")) {
            return false;
        }
        return BCrypt.checkpw(motDePasse, hash);
    }
}
