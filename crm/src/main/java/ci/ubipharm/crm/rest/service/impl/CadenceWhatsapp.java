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
        return dansLaPlage("whatsapp.envoi", 8, 18);
    }

    /** Plage propre aux envois en masse (ouverte en permanence par defaut), distincte de celle des relances. */
    boolean dansLaPlageEnvoiMasse() {
        return dansLaPlage("whatsapp.envoi_masse", 0, 24);
    }

    String plageEnvoiMasse() {
        return parametreService.entier("whatsapp.envoi_masse.heure_debut", 0) + " h et " + parametreService.entier("whatsapp.envoi_masse.heure_fin", 24) + " h";
    }

    private boolean dansLaPlage(String prefixe, int debut, int fin) {
        int heure = ZonedDateTime.now(ABIDJAN).getHour();
        return heure >= parametreService.entier(prefixe + ".heure_debut", debut) && heure < parametreService.entier(prefixe + ".heure_fin", fin);
    }

    /** Plage d'envoi lisible, par ex. « 8 h et 18 h ». */
    String plage() {
        return parametreService.entier("whatsapp.envoi.heure_debut", 8) + " h et " + parametreService.entier("whatsapp.envoi.heure_fin", 18) + " h";
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
