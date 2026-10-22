package ci.ubipharm.crm.rest.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import ci.ubipharm.crm.rest.service.dto.FichierDTO;

/** Fichier CSV lisible par Excel : UTF-8 avec BOM, point-virgule, aucune cellule interpretee comme formule. */
final class FichierCsv {

    private static final String TYPE_CSV = "text/csv;charset=UTF-8";
    private static final char SEPARATEUR = ';';

    private FichierCsv() {
    }

    static FichierDTO csv(String nom, List<List<String>> lignes) {
        StringBuilder texte = new StringBuilder("﻿");
        for (List<String> ligne : lignes) {
            texte.append(ligne.stream().map(FichierCsv::cellule).collect(Collectors.joining(String.valueOf(SEPARATEUR)))).append("\r\n");
        }
        return FichierDTO.builder().nom(nom).typeContenu(TYPE_CSV).contenu(texte.toString().getBytes(StandardCharsets.UTF_8)).build();
    }

    static String cellule(String valeur) {
        if (valeur == null) {
            return "";
        }
        // Une valeur commencant par = + - @ serait executee comme formule par Excel
        String sure = valeur.matches("^[=+\\-@].*") && !valeur.matches("^-?[0-9][0-9 .,]*$") ? "'" + valeur : valeur;
        return sure.contains(String.valueOf(SEPARATEUR)) || sure.contains("\"") || sure.contains("\n")
                ? "\"" + sure.replace("\"", "\"\"") + "\""
                : sure;
    }
}
