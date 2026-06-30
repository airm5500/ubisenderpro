package com.ubisenderpro.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

/**
 * Configure Jackson pour sérialiser correctement les types java.time (LocalDateTime)
 * et tolérer les saisies vides côté formulaires.
 *
 * <p>Les écrans envoient souvent des champs vides ("") pour des propriétés non
 * renseignées (nombres, dates, identifiants). Sans configuration, Jackson échoue
 * à convertir "" en Long/Integer/BigDecimal/LocalDate et renvoie une erreur
 * technique (500) <em>avant</em> la validation métier. On convertit donc toute
 * chaîne vide en {@code null} (la validation métier prend alors le relais avec un
 * message clair), et on ignore les propriétés inconnues pour éviter les
 * régressions quand un formulaire envoie un champ supplémentaire.</p>
 */
@Provider
@Produces(MediaType.APPLICATION_JSON)
public class ObjectMapperProvider implements ContextResolver<ObjectMapper> {

    private final ObjectMapper mapper;

    public ObjectMapperProvider() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        // Saisie vide ("") -> null pour tous les types scalaires (Long, Integer,
        // BigDecimal, LocalDate, enums...). Évite les erreurs techniques de
        // désérialisation et laisse la validation métier afficher un message clair.
        mapper.coercionConfigDefaults()
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull);
        // Tolère les champs inconnus envoyés par les formulaires (anti-régression).
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return mapper;
    }
}
