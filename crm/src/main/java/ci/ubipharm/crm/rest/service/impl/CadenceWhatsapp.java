package ci.ubipharm.crm.rest.service.impl;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import ci.ubipharm.crm.rest.service.ParametreService;
import lombok.RequiredArgsConstructor;

/**
 * Debit d'envoi de chaque numero WhatsApp, commun aux relances, promotions et envois en masse : plage horaire, attente
 * aleatoire entre deux messages d'un meme numero, plafond par jour tous envois confondus.
 */
@Component
@RequiredArgsConstructor
class CadenceWhatsapp {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final NamedParameterJdbcTemplate jdbc;
    private final ParametreService parametreService;

    /** Prochain envoi autorise par numero. */
    private final Map<String, Instant> prochainEnvoi = new ConcurrentHashMap<>();

    boolean dansLaPlage() {
        int heure = ZonedDateTime.now(ABIDJAN).getHour();
        return heure >= parametreService.entier("whatsapp.envoi.heure_debut", 8) && heure < parametreService.entier("whatsapp.envoi.heure_fin", 18);
    }

    /** Le numero a attendu assez depuis son dernier message et n'a pas atteint son plafond du jour. */
    boolean peutEnvoyer(String numero) {
        Instant prochain = prochainEnvoi.get(numero);
        if (prochain != null && prochain.isAfter(Instant.now())) {
            return false;
        }
        Long aujourdhui = jdbc.queryForObject("""
                select (select count(*) from crm_campagne_message where numero_expediteur = :numero and envoye_le >= utc_date())
                     + (select count(*) from crm_message_sortant where numero_expediteur = :numero and envoye_le >= utc_date())
                """, Map.of("numero", numero), Long.class);
        return aujourdhui == null || aujourdhui < parametreService.entier("whatsapp.envoi.plafond_jour", 200);
    }

    void envoye(String numero) {
        int min = parametreService.entier("whatsapp.envoi.delai_min_secondes", 20);
        int max = Math.max(min, parametreService.entier("whatsapp.envoi.delai_max_secondes", 45));
        prochainEnvoi.put(numero, Instant.now().plusSeconds(min == max ? min : ThreadLocalRandom.current().nextLong(min, max + 1L)));
    }
}
