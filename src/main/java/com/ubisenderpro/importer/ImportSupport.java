package com.ubisenderpro.importer;

import com.ubisenderpro.entity.ImportDetail;

import javax.persistence.EntityManager;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fonctions communes aux différents imports : sérialisation d'une ligne brute
 * et enregistrement d'un détail d'import (rejet, ignoré, etc.) pour export.
 */
public final class ImportSupport {

    private ImportSupport() {
    }

    /** Reconstitue le contenu d'une ligne sous forme "col=valeur; col=valeur". */
    public static String contenuLigne(Map<String, String> ligne) {
        if (ligne == null) return "";
        return ligne.entrySet().stream()
                .map(e -> e.getKey() + "=" + (e.getValue() == null ? "" : e.getValue()))
                .collect(Collectors.joining("; "));
    }

    /** Persiste une ligne en détail d'import (REJETE, IGNORE, ERREUR...). */
    public static void enregistrerDetail(EntityManager em, Long importId, int numeroLigne,
                                         String statut, String message, Map<String, String> ligne) {
        ImportDetail d = new ImportDetail();
        d.setImportId(importId);
        d.setNumeroLigne(numeroLigne);
        d.setStatut(statut);
        d.setMessage(message);
        d.setContenuLigne(contenuLigne(ligne));
        em.persist(d);
    }
}
