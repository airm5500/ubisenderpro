package ci.ubipharm.crm.sms.rest.service.impl;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import ci.ubipharm.crm.commun.rest.service.ParametreService;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Appels HTTP a un fournisseur SMS decrit en base (crm_fournisseur_sms) : rien n'est propre a un fournisseur dans le code.
 * Les gabarits (adresse, en-tetes, corps) acceptent {cle} {expediteur} {numero} {message} {reference} {url_accuse} {identifiant},
 * echappes selon l'endroit (JSON, formulaire, adresse). Les reponses se lisent par chemins JSON (a.b.c), plusieurs separes par
 * des virgules : le premier present l'emporte.
 */
@Slf4j
@Component
class PasserelleSmsHttp {

    private static final Pattern VARIABLE = Pattern.compile("\\{(cle|expediteur|numero|message|reference|url_accuse|identifiant)\\}");
    private static final Duration DELAI = Duration.ofSeconds(20);
    private static final int APERCU = 500;

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper jsonMapper;
    private final ParametreService parametreService;
    private final HttpClient http = HttpClient.newBuilder().proxy(java.net.ProxySelector.getDefault()).connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    PasserelleSmsHttp(NamedParameterJdbcTemplate jdbc, JsonMapper jsonMapper, ParametreService parametreService) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.parametreService = parametreService;
    }

    /** Parametrage complet d'un fournisseur, cle comprise : ne sort jamais du serveur. */
    record Configuration(Long id, String code, String libelle, boolean actif, String expediteur, String cle, String formatNumero, String urlEnvoi,
            String methodeEnvoi, String typeContenu, String entetes, String corps, String cheminIdentifiant, String cheminNbSms,
            String cheminErreur, String urlStatut, String cheminStatut, String champIdentifiantAccuse, String champStatutAccuse,
            String correspondanceStatuts, String jetonAccuse, String urlSolde, String cheminSolde) {

        /** Utilisable : actif, et sa cle renseignee si ses gabarits s'en servent. */
        boolean pret() {
            boolean besoinCle = contient(urlEnvoi) || contient(entetes) || contient(corps);
            return actif && (!besoinCle || cle != null && !cle.isBlank());
        }

        private static boolean contient(String gabarit) {
            return gabarit != null && gabarit.contains("{cle}");
        }
    }

    /**
     * Issue d'un appel : statut HTTP (0 = fournisseur injoignable), corps brut et valeurs lues.
     */
    record Reponse(int statutHttp, String corps, String identifiant, Integer nbSms, String erreur) {

        boolean reussie() {
            return statutHttp >= 200 && statutHttp < 300;
        }

        boolean injoignable() {
            return statutHttp == 0;
        }

        /** Cle ou compte refuse : inutile d'envoyer les messages suivants avant correction. */
        boolean accesRefuse() {
            return statutHttp == 401 || statutHttp == 403;
        }

        /** Erreur passagere cote fournisseur : on reessaie plus tard. */
        boolean passagere() {
            return injoignable() || statutHttp == 408 || statutHttp == 429 || statutHttp >= 500;
        }

        String apercu() {
            return corps == null ? null : corps.length() > APERCU ? corps.substring(0, APERCU) + "…" : corps;
        }
    }

    private static final String SELECT = """
            select id, code, libelle, actif, expediteur, cle_api, format_numero, url_envoi, methode_envoi, type_contenu, entetes, corps,
                   chemin_identifiant, chemin_nb_sms, chemin_erreur, url_statut, chemin_statut, champ_identifiant_accuse, champ_statut_accuse,
                   correspondance_statuts, jeton_accuse, url_solde, chemin_solde
            from crm_fournisseur_sms
            """;

    Configuration parId(Long id) {
        return premiere(jdbc.query(SELECT + " where id = :id", Map.of("id", id), (rs, i) -> lire(rs)));
    }

    Configuration parCode(String code) {
        return premiere(jdbc.query(SELECT + " where code = :code", Map.of("code", code), (rs, i) -> lire(rs)));
    }

    /** Fournisseur par defaut, actif ou non ; null s'il n'y en a aucun. */
    Configuration parDefaut() {
        return premiere(jdbc.query(SELECT + " where par_defaut = true", Map.of(), (rs, i) -> lire(rs)));
    }

    private static Configuration premiere(List<Configuration> liste) {
        return liste.isEmpty() ? null : liste.getFirst();
    }

    private static Configuration lire(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Configuration(rs.getLong("id"), rs.getString("code"), rs.getString("libelle"), rs.getBoolean("actif"), rs.getString("expediteur"),
                rs.getString("cle_api"), rs.getString("format_numero"), rs.getString("url_envoi"), rs.getString("methode_envoi"),
                rs.getString("type_contenu"), rs.getString("entetes"), rs.getString("corps"), rs.getString("chemin_identifiant"),
                rs.getString("chemin_nb_sms"), rs.getString("chemin_erreur"), rs.getString("url_statut"), rs.getString("chemin_statut"),
                rs.getString("champ_identifiant_accuse"), rs.getString("champ_statut_accuse"), rs.getString("correspondance_statuts"),
                rs.getString("jeton_accuse"), rs.getString("url_solde"), rs.getString("chemin_solde"));
    }

    /** Envoie un SMS ; ne leve jamais d'exception, l'issue est dans la reponse. */
    Reponse envoyer(Configuration c, String numero, String message, String reference) {
        Map<String, String> valeurs = new HashMap<>();
        valeurs.put("cle", nonNul(c.cle()));
        valeurs.put("expediteur", nonNul(c.expediteur()));
        valeurs.put("numero", numero(c, numero));
        valeurs.put("message", message);
        valeurs.put("reference", nonNul(reference));
        valeurs.put("url_accuse", nonNul(urlAccuse(c)));
        Reponse brute = appeler(c, c.methodeEnvoi(), c.urlEnvoi(), c.entetes(), c.corps(), valeurs);
        if (brute.injoignable()) {
            return brute;
        }
        JsonNode json = analyser(brute.corps());
        String identifiant = lire(json, c.cheminIdentifiant());
        String parts = lire(json, c.cheminNbSms());
        String erreur = brute.reussie() ? null : erreur(json, c.cheminErreur(), brute.statutHttp());
        return new Reponse(brute.statutHttp(), brute.corps(), identifiant, entier(parts), erreur);
    }

    /** Statut CRM (ENVOYE, DISTRIBUE, ECHEC) d'un message interroge chez le fournisseur ; null si inconnu ou injoignable. */
    String statut(Configuration c, String identifiant) {
        if (c.urlStatut() == null || c.urlStatut().isBlank()) {
            return null;
        }
        Reponse reponse = appeler(c, "GET", c.urlStatut(), c.entetes(), null, Map.of("cle", nonNul(c.cle()), "identifiant", identifiant));
        if (!reponse.reussie()) {
            return null;
        }
        return correspondance(c, lire(analyser(reponse.corps()), c.cheminStatut()));
    }

    Reponse solde(Configuration c) {
        return appeler(c, "GET", c.urlSolde(), c.entetes(), null, Map.of("cle", nonNul(c.cle())));
    }

    String lireSolde(Configuration c, Reponse reponse) {
        return lire(analyser(reponse.corps()), c.cheminSolde());
    }

    /** Traduit un statut du fournisseur par la table "SENT=ENVOYE; DELIVERED=DISTRIBUE; ..." ; null si absent de la table. */
    static String correspondance(Configuration c, String statutFournisseur) {
        if (statutFournisseur == null || c.correspondanceStatuts() == null) {
            return null;
        }
        for (String paire : c.correspondanceStatuts().split("[;\\n]")) {
            String[] cv = paire.split("=", 2);
            if (cv.length == 2 && cv[0].trim().equalsIgnoreCase(statutFournisseur.trim())) {
                return cv[1].trim().toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    /** Adresse ou le fournisseur pousse ses accuses ; null tant que l'adresse publique du CRM n'est pas renseignee. */
    String urlAccuse(Configuration c) {
        String base = parametreService.valeur("application.url_publique", "").trim();
        if (base.isEmpty()) {
            return null;
        }
        return base.replaceAll("/+$", "") + "/api/passerelle/sms/" + c.code() + "/accuse?jeton=" + c.jetonAccuse();
    }

    /** Numero au format attendu par le fournisseur, depuis le numero du CRM (225 + 10 chiffres). */
    static String numero(Configuration c, String numero) {
        String chiffres = numero == null ? "" : numero.replaceAll("\\D", "");
        if (chiffres.startsWith("00")) {
            chiffres = chiffres.substring(2);
        }
        if (chiffres.length() == 10) {
            chiffres = "225" + chiffres;
        }
        return switch (c.formatNumero() == null ? "INTERNATIONAL" : c.formatNumero()) {
            case "PLUS" -> "+" + chiffres;
            case "LOCAL" -> chiffres.startsWith("225") && chiffres.length() == 13 ? chiffres.substring(3) : chiffres;
            default -> chiffres;
        };
    }

    /** Valeur lue par l'un des chemins (separes par des virgules) dans un JSON ; null si aucun n'existe. */
    static String lire(JsonNode json, String chemins) {
        if (json == null || chemins == null || chemins.isBlank()) {
            return null;
        }
        for (String chemin : chemins.split(",")) {
            JsonNode noeud = json;
            for (String morceau : chemin.trim().split("\\.")) {
                noeud = morceau.matches("\\d+") && noeud.isArray() ? noeud.path(Integer.parseInt(morceau)) : noeud.path(morceau);
            }
            if (noeud.isValueNode() && !noeud.isNull()) {
                return noeud.asString();
            }
            if (noeud.isArray() && !noeud.isEmpty() && noeud.path(0).isValueNode()) {
                return noeud.path(0).asString();
            }
        }
        return null;
    }

    /** Valeur d'un accuse : JSON du corps, a defaut parametres de l'adresse (accuse en GET). */
    static String lireAccuse(JsonNode json, Map<String, String> parametres, String champs) {
        String valeur = lire(json, champs);
        if (valeur != null || champs == null) {
            return valeur;
        }
        for (String champ : champs.split(",")) {
            String v = parametres.get(champ.trim());
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    JsonNode analyser(String corps) {
        if (corps == null || corps.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readTree(corps);
        } catch (JacksonException e) {
            return null;
        }
    }

    private Reponse appeler(Configuration c, String methode, String gabaritUrl, String gabaritEntetes, String gabaritCorps, Map<String, String> valeurs) {
        String typeContenu = c.typeContenu() == null || c.typeContenu().isBlank() ? "application/json" : c.typeContenu().trim();
        boolean json = typeContenu.toLowerCase(Locale.ROOT).contains("json");
        try {
            URI adresse = URI.create(remplacer(gabaritUrl, valeurs, v -> URLEncoder.encode(v, StandardCharsets.UTF_8)));
            HttpRequest.Builder requete = HttpRequest.newBuilder(adresse).timeout(DELAI).header("Accept", "application/json");
            if (gabaritEntetes != null) {
                for (String ligne : gabaritEntetes.split("\\r?\\n")) {
                    int deuxPoints = ligne.indexOf(':');
                    if (deuxPoints > 0) {
                        requete.header(ligne.substring(0, deuxPoints).trim(), remplacer(ligne.substring(deuxPoints + 1).trim(), valeurs, v -> v.replaceAll("[\\r\\n]", "")));
                    }
                }
            }
            if ("GET".equals(methode) || gabaritCorps == null || gabaritCorps.isBlank()) {
                requete.method(methode, HttpRequest.BodyPublishers.noBody());
            } else {
                String corps = json ? corpsJson(gabaritCorps, valeurs)
                        : remplacer(gabaritCorps, valeurs, v -> URLEncoder.encode(v, StandardCharsets.UTF_8));
                requete.header("Content-Type", typeContenu).method(methode, HttpRequest.BodyPublishers.ofString(corps, StandardCharsets.UTF_8));
            }
            HttpResponse<String> reponse = http.send(requete.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Reponse(reponse.statusCode(), reponse.body(), null, null, null);
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Fournisseur SMS {} injoignable : {}", c.code(), e.getMessage());
            return new Reponse(0, null, null, null, "Fournisseur SMS injoignable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Reponse(0, null, null, null, "Fournisseur SMS injoignable");
        }
    }

    /** Corps JSON : valeurs echappees ; un champ de premier niveau reste vide (url_accuse non renseignee...) est retire. */
    private String corpsJson(String gabarit, Map<String, String> valeurs) {
        String corps = remplacer(gabarit, valeurs, v -> {
            String echappe = jsonMapper.writeValueAsString(v);
            return echappe.substring(1, echappe.length() - 1);
        });
        JsonNode arbre = analyser(corps);
        if (arbre instanceof ObjectNode objet) {
            List<String> vides = objet.properties().stream().filter(e -> e.getValue().isString() && e.getValue().asString().isEmpty())
                    .map(Map.Entry::getKey).toList();
            vides.forEach(objet::remove);
            return jsonMapper.writeValueAsString(objet);
        }
        return corps;
    }

    private static String remplacer(String gabarit, Map<String, String> valeurs, UnaryOperator<String> echapper) {
        Matcher m = VARIABLE.matcher(gabarit);
        StringBuilder resultat = new StringBuilder();
        while (m.find()) {
            String valeur = valeurs.get(m.group(1));
            m.appendReplacement(resultat, Matcher.quoteReplacement(valeur == null ? m.group() : echapper.apply(valeur)));
        }
        m.appendTail(resultat);
        return resultat.toString();
    }

    private static String erreur(JsonNode json, String chemin, int statutHttp) {
        String message = lire(json, chemin);
        if (message == null) {
            message = "réponse HTTP " + statutHttp;
        }
        return message.length() > 180 ? message.substring(0, 180) + "…" : message;
    }

    private static Integer entier(String texte) {
        try {
            return texte == null ? null : Integer.valueOf(texte.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nonNul(String texte) {
        return texte == null ? "" : texte;
    }
}
