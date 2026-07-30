package com.ubisenderpro.config;

import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.json.bind.serializer.DeserializationContext;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.stream.JsonParser;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Rend JSON-B (Yasson) tolérant sur les dates envoyées par les écrans.
 *
 * <p><b>Pourquoi ce fournisseur ?</b> Payara 5 donne la priorité à JSON-B sur
 * Jackson pour la lecture des corps JSON de JAX-RS : la configuration de
 * {@link ObjectMapperProvider} (chaîne vide convertie en {@code null}) ne
 * s'applique donc pas aux requêtes entrantes. Un champ date laissé vide par
 * l'utilisateur arrivait sous la forme {@code "dateLivraison": ""} et Yasson
 * échouait avec une erreur technique (500) <em>avant même</em> la validation
 * métier — l'utilisateur ne voyait qu'un « échec technique » sans savoir quoi
 * corriger.</p>
 *
 * <p>Trois désérialiseurs sont enregistrés, un par type temporel utilisé dans les
 * entités. Ils appliquent la même règle&nbsp;:</p>
 * <ul>
 *   <li>valeur absente, nulle ou vide (espaces compris) → {@code null}, c'est-à-dire
 *       « non renseigné » ; la validation métier reprend la main si le champ est
 *       obligatoire ;</li>
 *   <li>date seule ou date+heure acceptées indifféremment (les écrans ExtJS
 *       n'envoient pas toujours le même format selon le champ).</li>
 * </ul>
 *
 * <p>Le reste de la configuration JSON-B est laissé aux valeurs par défaut de
 * Yasson (nommage des propriétés à l'identique, dates ISO, propriétés nulles
 * omises) : le format des réponses est donc strictement inchangé.</p>
 */
@Provider
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class JsonbProvider implements ContextResolver<Jsonb> {

    private final Jsonb jsonb;

    public JsonbProvider() {
        jsonb = JsonbBuilder.create(new JsonbConfig().withDeserializers(
                new DateTolerante(), new DateHeureTolerante(), new HeureTolerante()));
    }

    @Override
    public Jsonb getContext(Class<?> type) {
        return jsonb;
    }

    /* ------------------------------------------------------------------ */
    /* Conversions (isolées et testables : aucune dépendance à JSON-P)      */
    /* ------------------------------------------------------------------ */

    /**
     * Texte utile d'une valeur JSON : {@code null} si vide ou uniquement des espaces.
     */
    static String texte(String brut) {
        if (brut == null) { return null; }
        String s = brut.trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * {@code ""} → {@code null}. Accepte « 2026-07-29 » comme « 2026-07-29T08:30:00 »
     * (seule la partie date est retenue).
     */
    static LocalDate versDate(String brut) {
        String s = texte(brut);
        if (s == null) { return null; }
        int t = s.indexOf('T');
        return LocalDate.parse(t > 0 ? s.substring(0, t) : s);
    }

    /**
     * {@code ""} → {@code null}. Accepte « 2026-07-29T08:30:00 » comme « 2026-07-29 »
     * (dans ce cas l'heure est fixée à minuit).
     */
    static LocalDateTime versDateHeure(String brut) {
        String s = texte(brut);
        if (s == null) { return null; }
        if (s.indexOf('T') < 0) { return LocalDate.parse(s).atStartOfDay(); }
        return LocalDateTime.parse(s);
    }

    /** {@code ""} → {@code null}. Accepte « 08:30 » comme « 08:30:00 ». */
    static LocalTime versHeure(String brut) {
        String s = texte(brut);
        return s == null ? null : LocalTime.parse(s);
    }

    /* ------------------------------------------------------------------ */
    /* Désérialiseurs JSON-B                                               */
    /* ------------------------------------------------------------------ */

    /**
     * Valeur courante du parseur sous forme de texte, {@code null} pour un
     * {@code null} JSON. On passe par {@code getValue()} (JSON-P 1.1) et on
     * retombe sur {@code getString()} pour les implémentations qui ne
     * l'exposent pas à cet emplacement.
     */
    private static String lire(JsonParser parser) {
        try {
            JsonValue v = parser.getValue();
            if (v == null || v.getValueType() == JsonValue.ValueType.NULL) { return null; }
            return v.getValueType() == JsonValue.ValueType.STRING
                    ? ((JsonString) v).getString() : v.toString();
        } catch (RuntimeException e) {
            try { return parser.getString(); } catch (RuntimeException ignore) { return null; }
        }
    }

    static final class DateTolerante implements JsonbDeserializer<LocalDate> {
        @Override
        public LocalDate deserialize(JsonParser parser, DeserializationContext ctx, Type type) {
            return versDate(lire(parser));
        }
    }

    static final class DateHeureTolerante implements JsonbDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext ctx, Type type) {
            return versDateHeure(lire(parser));
        }
    }

    static final class HeureTolerante implements JsonbDeserializer<LocalTime> {
        @Override
        public LocalTime deserialize(JsonParser parser, DeserializationContext ctx, Type type) {
            return versHeure(lire(parser));
        }
    }
}
