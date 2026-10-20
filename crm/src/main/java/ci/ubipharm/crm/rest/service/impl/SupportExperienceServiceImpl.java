package ci.ubipharm.crm.rest.service.impl;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.SupportExperienceService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.SupportExperienceDTO;
import ci.ubipharm.crm.rest.service.dto.SupportExperienceLigneDTO;
import ci.ubipharm.crm.rest.service.dto.SupportExperiencePointDTO;
import ci.ubipharm.crm.rest.service.dto.SupportExperienceUtilisateurDTO;
import ci.ubipharm.crm.rest.service.dto.SupportMesureDTO;
import lombok.RequiredArgsConstructor;

/**
 * Le navigateur n'est pas cru sur parole : types limites a ECRAN et API, adresses renormalisees, nombres et durees bornes.
 * Un envoi = au plus une ecriture (INSERT multi-lignes qui cumule dans la tranche de 5 minutes).
 */
@Service
@RequiredArgsConstructor
public class SupportExperienceServiceImpl implements SupportExperienceService {

    static final int MAX_LIGNES = 300;
    static final long MAX_DUREE_MS = 600_000;
    static final long MAX_NOMBRE = 100_000;
    private static final int MAX_CLE = 160;
    private static final Pattern METHODE = Pattern.compile("(GET|POST|PUT|PATCH|DELETE)");
    /** Segment variable : nombre, UUID, ou mot d'au moins 6 caracteres contenant un chiffre (reference, jeton). */
    private static final Pattern VARIABLE = Pattern.compile("\\d+|[0-9a-fA-F-]{32,36}|(?=[^/]*\\d)[^/]{6,}");

    private final JdbcTemplate jdbc;
    private final ParametreService parametreService;
    private final UtilisateurCourantService utilisateurCourantService;

    @Override
    @Transactional
    public boolean enregistrer(List<SupportMesureDTO> mesures) {
        if (!parametreService.oui("support.rum_actif", true)) {
            return false;
        }
        if (mesures == null || mesures.isEmpty()) {
            return true;
        }
        Map<String, long[]> cumuls = new LinkedHashMap<>();
        for (SupportMesureDTO m : mesures.subList(0, Math.min(mesures.size(), MAX_LIGNES))) {
            String type = m.getType() == null ? "" : m.getType().trim().toUpperCase(Locale.ROOT);
            String cle = "API".equals(type) ? cleAppel(m.getCle()) : "ECRAN".equals(type) ? cleEcran(m.getCle()) : null;
            long nombre = Math.min(m.getNombre(), MAX_NOMBRE);
            if (cle == null || nombre <= 0) {
                continue;
            }
            long[] c = cumuls.computeIfAbsent(type + "|" + cle, k -> new long[6]);
            long max = borne(m.getMaxMs(), 0, MAX_DUREE_MS);
            c[0] += nombre;
            c[1] += borne(m.getTotalMs(), 0, nombre * MAX_DUREE_MS);
            c[2] = Math.max(c[2], max);
            c[3] += borne(m.getNb1s(), 0, nombre);
            c[4] += borne(m.getNb3s(), 0, nombre);
            c[5] += borne(m.getEchecs(), 0, nombre);
        }
        if (cumuls.isEmpty()) {
            return true;
        }
        Timestamp tranche = Timestamp.valueOf(tranche(LocalDateTime.now(ZoneOffset.UTC)));
        Long utilisateur = utilisateurCourantService.utilisateur().getId();
        StringBuilder sql = new StringBuilder(
                "insert into crm_support_rum (tranche, utilisateur_id, type_mesure, cle, nombre, total_ms, max_ms, nb_1s, nb_3s, echecs) values ");
        List<Object> valeurs = new ArrayList<>();
        cumuls.forEach((typeCle, c) -> {
            int i = typeCle.indexOf('|');
            sql.append(valeurs.isEmpty() ? "" : ", ").append("(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            valeurs.addAll(List.of(tranche, utilisateur, typeCle.substring(0, i), typeCle.substring(i + 1), c[0], c[1], c[2], c[3], c[4], c[5]));
        });
        sql.append("""
                 on duplicate key update nombre = nombre + values(nombre), total_ms = total_ms + values(total_ms),
                                         max_ms = greatest(max_ms, values(max_ms)), nb_1s = nb_1s + values(nb_1s),
                                         nb_3s = nb_3s + values(nb_3s), echecs = echecs + values(echecs)
                """);
        jdbc.update(sql.toString(), valeurs.toArray());
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public SupportExperienceDTO consulter(int heures) {
        int h = (int) borne(heures, 1, 336);
        Timestamp depuis = Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minusHours(h));
        String agregats = """
                sum(nombre) as nombre, round(sum(total_ms) / sum(nombre)) as moyenne, max(max_ms) as pire,
                round(100 * sum(nb_1s) / sum(nombre), 1) as part1s, round(100 * sum(nb_3s) / sum(nombre), 1) as part3s, sum(echecs) as echecs
                """;
        List<SupportExperienceLigneDTO> resume = jdbc.query("select type_mesure as cle, " + agregats
                + " from crm_support_rum where tranche >= ? group by type_mesure order by type_mesure", (rs, i) -> ligne(rs), depuis);
        String parCle = "select cle, " + agregats + " from crm_support_rum where type_mesure = ? and tranche >= ? group by cle ";
        List<SupportExperienceLigneDTO> ecrans = jdbc.query(parCle + "order by moyenne desc, nombre desc limit 30", (rs, i) -> ligne(rs), "ECRAN", depuis);
        List<SupportExperienceLigneDTO> appels = jdbc.query(parCle + "order by moyenne desc, nombre desc limit 30", (rs, i) -> ligne(rs), "API", depuis);
        List<SupportExperienceLigneDTO> echecs = jdbc.query(parCle + "having sum(echecs) > 0 order by echecs desc, nombre desc limit 15",
                (rs, i) -> ligne(rs), "API", depuis);
        List<SupportExperienceUtilisateurDTO> utilisateurs = jdbc.query("""
                select coalesce(u.nom_complet, '—') as utilisateur,
                       sum(case when r.type_mesure = 'API' then r.nombre else 0 end) as appels,
                       round(sum(case when r.type_mesure = 'API' then r.total_ms else 0 end)
                             / nullif(sum(case when r.type_mesure = 'API' then r.nombre else 0 end), 0)) as moyenneAppel,
                       round(100 * sum(case when r.type_mesure = 'API' then r.nb_3s else 0 end)
                             / nullif(sum(case when r.type_mesure = 'API' then r.nombre else 0 end), 0), 1) as part3s,
                       sum(r.echecs) as echecs,
                       sum(case when r.type_mesure = 'ECRAN' then r.nombre else 0 end) as ecrans,
                       round(sum(case when r.type_mesure = 'ECRAN' then r.total_ms else 0 end)
                             / nullif(sum(case when r.type_mesure = 'ECRAN' then r.nombre else 0 end), 0)) as moyenneEcran,
                       max(r.tranche) as derniere
                from crm_support_rum r
                left join crm_utilisateur u on u.id = r.utilisateur_id
                where r.tranche >= ?
                group by r.utilisateur_id, u.nom_complet
                order by moyenneAppel desc
                limit 100
                """, (rs, i) -> SupportExperienceUtilisateurDTO.builder()
                .utilisateur(rs.getString("utilisateur")).appels(rs.getLong("appels")).moyenneAppelMs(rs.getLong("moyenneAppel"))
                .part3s(rs.getDouble("part3s")).echecs(rs.getLong("echecs")).ecrans(rs.getLong("ecrans"))
                .moyenneEcranMs(rs.getLong("moyenneEcran")).derniereActivite(rs.getTimestamp("derniere").toLocalDateTime()).build(), depuis);
        // Par tranche jusqu'a 6 h, par heure au-dela
        String point = h <= 6 ? "tranche" : "date_format(tranche, '%Y-%m-%d %H:00:00')";
        List<SupportExperiencePointDTO> serie = jdbc.query("select " + point + " as le, " + """
                       sum(case when type_mesure = 'API' then nombre else 0 end) as appels,
                       round(sum(case when type_mesure = 'API' then total_ms else 0 end)
                             / nullif(sum(case when type_mesure = 'API' then nombre else 0 end), 0)) as moyenneAppel,
                       round(sum(case when type_mesure = 'ECRAN' then total_ms else 0 end)
                             / nullif(sum(case when type_mesure = 'ECRAN' then nombre else 0 end), 0)) as moyenneEcran,
                       round(100 * sum(case when type_mesure = 'API' then nb_3s else 0 end)
                             / nullif(sum(case when type_mesure = 'API' then nombre else 0 end), 0), 1) as part3s
                from crm_support_rum where tranche >= ? group by le order by le
                """, (rs, i) -> SupportExperiencePointDTO.builder()
                .le(rs.getTimestamp("le").toLocalDateTime()).appels(rs.getLong("appels")).moyenneAppelMs(rs.getLong("moyenneAppel"))
                .moyenneEcranMs(rs.getLong("moyenneEcran")).part3s(rs.getDouble("part3s")).build(), depuis);
        return SupportExperienceDTO.builder().heures(h).actif(parametreService.oui("support.rum_actif", true)).resume(resume).ecrans(ecrans)
                .appels(appels).echecs(echecs).utilisateurs(utilisateurs).serie(serie).build();
    }

    @Override
    @Transactional
    public int purger() {
        int jours = parametreService.entier("support.rum_retention_jours", 14);
        Timestamp limite = Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minusDays(jours));
        int total = 0;
        int n;
        // Par paquets : la table n'est jamais verrouillee longtemps
        do {
            n = jdbc.update("delete from crm_support_rum where tranche < ? limit 5000", limite);
            total += n;
        } while (n == 5000);
        return total;
    }

    static LocalDateTime tranche(LocalDateTime instant) {
        LocalDateTime minute = instant.truncatedTo(ChronoUnit.MINUTES);
        return minute.minusMinutes(minute.getMinute() % 5);
    }

    /** « GET /api/factures/{id} » : methode reconnue, adresse /api/ sans parametres, segments variables remplaces ; null si invalide. */
    public static String cleAppel(String cle) {
        if (cle == null) {
            return null;
        }
        String[] parties = cle.trim().split("\\s+", 2);
        if (parties.length != 2 || !METHODE.matcher(parties[0].toUpperCase(Locale.ROOT)).matches()) {
            return null;
        }
        String chemin = parties[1];
        int fin = indexFin(chemin);
        chemin = chemin.substring(0, fin);
        if (!chemin.startsWith("/api/") || chemin.startsWith("/api/support/rum")) {
            return null;
        }
        StringBuilder normalise = new StringBuilder(parties[0].toUpperCase(Locale.ROOT)).append(' ');
        for (String segment : chemin.substring(1).split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            normalise.append('/').append(VARIABLE.matcher(segment).matches() ? "{id}" : segment);
        }
        return tronquer(normalise.toString());
    }

    static String cleEcran(String cle) {
        if (cle == null) {
            return null;
        }
        String titre = cle.replaceAll("\\s+", " ").trim();
        return titre.isEmpty() ? null : tronquer(titre);
    }

    private static int indexFin(String chemin) {
        int q = chemin.indexOf('?');
        int d = chemin.indexOf('#');
        int fin = chemin.length();
        if (q >= 0) {
            fin = q;
        }
        if (d >= 0 && d < fin) {
            fin = d;
        }
        return fin;
    }

    private static String tronquer(String s) {
        return s.length() > MAX_CLE ? s.substring(0, MAX_CLE) : s;
    }

    private static long borne(long v, long min, long max) {
        return Math.max(min, Math.min(max, v));
    }

    private static SupportExperienceLigneDTO ligne(java.sql.ResultSet rs) throws java.sql.SQLException {
        return SupportExperienceLigneDTO.builder().cle(rs.getString("cle")).nombre(rs.getLong("nombre")).moyenneMs(rs.getLong("moyenne"))
                .maxMs(rs.getLong("pire")).part1s(rs.getDouble("part1s")).part3s(rs.getDouble("part3s")).echecs(rs.getLong("echecs")).build();
    }
}
