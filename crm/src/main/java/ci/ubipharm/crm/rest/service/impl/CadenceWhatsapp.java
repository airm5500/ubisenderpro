package ci.ubipharm.crm.rest.service.impl;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.dto.ResultatEnvoiDTO;
import ci.ubipharm.crm.rest.service.impl.ReglesCadence.Etat;
import ci.ubipharm.crm.rest.service.impl.ReglesCadence.Reglages;
import ci.ubipharm.crm.rest.service.impl.ReglesCadence.Verdict;

/**
 * Debit d'envoi de chaque numero WhatsApp, commun aux relances, promotions et envois en masse : plage horaire, attente
 * aleatoire entre deux messages d'un meme numero, longue pause, plafonds par jour et par heure, montee en charge,
 * pause de protection apres des echecs techniques (regles dans {@link ReglesCadence}). L'etat de chaque numero est en
 * base (crm_session_whatsapp) : un redemarrage ne le remet pas a zero.
 */
@Component
class CadenceWhatsapp {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");
    private static final String ENVOYES = "('ENVOYE', 'DISTRIBUE', 'LU')";

    private final NamedParameterJdbcTemplate jdbc;
    private final ParametreService parametreService;
    /** Attente minimale imposee quels que soient les parametres (0 dans les tests d'integration). */
    private final int plancher;

    CadenceWhatsapp(NamedParameterJdbcTemplate jdbc, ParametreService parametreService,
                    @Value("${crm.whatsapp.plancher-secondes:" + ReglesCadence.PLANCHER_SECONDES + "}") int plancher) {
        this.jdbc = jdbc;
        this.parametreService = parametreService;
        this.plancher = plancher;
    }

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

    /** Le numero n'est ni en attente ni en pause et n'a atteint aucun de ses plafonds. */
    boolean peutEnvoyer(String numero) {
        return verdict(numero).autorise();
    }

    /** Envoi autorise, ou la raison du refus et l'heure de reprise (UTC, heure d'Abidjan). */
    Verdict verdict(String numero) {
        return ReglesCadence.verdict(reglages(), etat(numero), maintenant());
    }

    /** Plafond du jour du numero, reduit s'il est en montee en charge. */
    int plafondDuJour(String numero) {
        return ReglesCadence.plafondDuJour(reglages(), etat(numero).monteeDepuis(), maintenant().toLocalDate());
    }

    /**
     * Apres chaque essai : attente avant le suivant, longue pause au bout d'une serie de messages reussis, repos du
     * numero apres des echecs techniques de suite. Un numero absent de WhatsApp n'est pas un echec technique.
     */
    void apres(String numero, ResultatEnvoiDTO resultat) {
        Reglages r = reglages();
        List<Map<String, Object>> lignes = jdbc.queryForList("select serie, echecs_consecutifs from crm_session_whatsapp where numero = :numero",
                Map.of("numero", numero));
        int serie = lignes.isEmpty() ? 0 : ((Number) lignes.getFirst().get("serie")).intValue();
        int echecs = lignes.isEmpty() ? 0 : ((Number) lignes.getFirst().get("echecs_consecutifs")).intValue();
        LocalDateTime maintenant = maintenant();
        LocalDateTime reprise = maintenant.plus(ReglesCadence.attente(r, 0, ThreadLocalRandom.current()));
        String motif = null;
        if (resultat.isSucces()) {
            serie++;
            echecs = 0;
            if (ReglesCadence.pauseLongue(r, serie)) {
                reprise = maintenant.plus(ReglesCadence.attente(r, serie, ThreadLocalRandom.current()));
                motif = "Pause longue après " + serie + " messages";
                serie = 0;
            }
        } else if (resultat.isErreurTechnique()) {
            echecs++;
            if (ReglesCadence.pauseProtection(r, echecs)) {
                reprise = maintenant.plusMinutes(r.pauseEchecMinutes());
                motif = "Pause de protection après " + echecs + " échecs techniques de suite";
                echecs = 0;
            }
        }
        jdbc.update("""
                update crm_session_whatsapp set reprise_le = :reprise, reprise_motif = :motif, serie = :serie, echecs_consecutifs = :echecs
                where numero = :numero
                """, new MapSqlParameterSource("numero", numero).addValue("reprise", Timestamp.valueOf(reprise.truncatedTo(ChronoUnit.SECONDS))).addValue("motif", motif)
                .addValue("serie", serie).addValue("echecs", echecs));
    }

    /** Meme texte deja parti aujourd'hui vers ce destinataire, depuis n'importe quel numero. */
    boolean doublonDuJour(String destinataire, String contenu) {
        if (destinataire == null || contenu == null) {
            return false;
        }
        Long n = jdbc.queryForObject("""
                select (select count(*) from crm_campagne_message where numero_destinataire = :d and envoye_le >= utc_date() and statut in %s
                            and canal = 'WHATSAPP' and contenu = :c)
                     + (select count(*) from crm_message_sortant where numero_destinataire = :d and envoye_le >= utc_date() and statut in %s
                            and contenu = :c)
                """.formatted(ENVOYES, ENVOYES), Map.of("d", destinataire, "c", contenu), Long.class);
        return n != null && n > 0;
    }

    private Etat etat(String numero) {
        Map<String, Object> l = jdbc.queryForMap("""
                select (select reprise_le from crm_session_whatsapp where numero = :numero) as reprise,
                       (select reprise_motif from crm_session_whatsapp where numero = :numero) as motif,
                       (select montee_depuis from crm_session_whatsapp where numero = :numero) as montee,
                       (select count(*) from crm_campagne_message where numero_expediteur = :numero and envoye_le >= utc_date())
                     + (select count(*) from crm_message_sortant where numero_expediteur = :numero and envoye_le >= utc_date()) as jour,
                       (select count(*) from crm_campagne_message where numero_expediteur = :numero and envoye_le >= utc_timestamp() - interval 1 hour)
                     + (select count(*) from crm_message_sortant where numero_expediteur = :numero and envoye_le >= utc_timestamp() - interval 1 hour) as heure,
                       (select min(x.envoye_le) from (
                            select envoye_le from crm_campagne_message where numero_expediteur = :numero and envoye_le >= utc_timestamp() - interval 1 hour
                            union all
                            select envoye_le from crm_message_sortant where numero_expediteur = :numero and envoye_le >= utc_timestamp() - interval 1 hour) x) as ancien
                """, Map.of("numero", numero));
        Object montee = l.get("montee");
        return new Etat(dateHeure(l.get("reprise")), (String) l.get("motif"), ((Number) l.get("jour")).intValue(), ((Number) l.get("heure")).intValue(),
                dateHeure(l.get("ancien")), montee instanceof Date d ? d.toLocalDate() : (LocalDate) montee);
    }

    private Reglages reglages() {
        return new Reglages(
                parametreService.entier("whatsapp.envoi.delai_min_secondes", 20),
                parametreService.entier("whatsapp.envoi.delai_max_secondes", 45),
                plancher,
                parametreService.entier("whatsapp.envoi.pause_apres_messages", 15),
                parametreService.entier("whatsapp.envoi.pause_min_minutes", 5),
                parametreService.entier("whatsapp.envoi.pause_max_minutes", 12),
                parametreService.entier("whatsapp.envoi.plafond_jour", 200),
                parametreService.entier("whatsapp.envoi.plafond_heure", 40),
                parametreService.entier("whatsapp.envoi.montee_depart", 20),
                parametreService.entier("whatsapp.envoi.montee_pas", 20),
                parametreService.entier("whatsapp.envoi.echecs_avant_pause", 3),
                parametreService.entier("whatsapp.envoi.pause_echec_minutes", 30));
    }

    private static LocalDateTime maintenant() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static LocalDateTime dateHeure(Object valeur) {
        if (valeur instanceof Timestamp t) {
            return t.toLocalDateTime();
        }
        if (valeur instanceof LocalDateTime d) {
            return d;
        }
        return null;
    }
}
