package com.ubisenderpro.service;

import java.time.Year;
import java.util.function.Predicate;

/**
 * Génération de codes par défaut pour les créations (promotions, informations,
 * événements de disponibilité, marques, catégories…).
 *
 * <p>Format : {@code PREFIXE-ANNEE-NNNN} (ex. {@code PROMO-2026-0007}). Le numéro
 * est incrémenté jusqu'à obtenir un code libre selon le test d'unicité fourni par
 * l'appelant. Le code reste modifiable par l'utilisateur : il n'est généré que
 * lorsqu'aucun code n'a été saisi (création manuelle ou import).</p>
 */
public final class Codes {

    private Codes() {}

    /** Génère un code unique {@code PREFIXE-ANNEE-NNNN} (incrémente jusqu'à libre). */
    public static String generer(String prefixe, Predicate<String> existeDeja) {
        String base = prefixe + "-" + Year.now().getValue() + "-";
        int n = 1;
        String code;
        do {
            code = base + String.format("%04d", n++);
        } while (existeDeja.test(code));
        return code;
    }
}
