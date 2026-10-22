package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.AnalyseCampagneService;
import ci.ubipharm.crm.rest.service.dto.AnalyseCampagneDTO;
import ci.ubipharm.crm.rest.service.dto.PointEvolutionDTO;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnalyseCampagneServiceImpl implements AnalyseCampagneService {

    /** Au-dela, la courbe ne garde que les jours d'activite. */
    private static final int JOURS_MAX = 90;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public AnalyseCampagneDTO analyser(Long campagneId) {
        Map<String, Object> p = Map.of("id", campagneId);
        Map<String, Object> c = jdbc.queryForMap("""
                select sum(statut not in ('EXCLU', 'ANNULE')) as cibles,
                       sum(envoye_le is not null) as envoyes,
                       sum(distribue_le is not null or statut in ('DISTRIBUE', 'LU')) as distribues,
                       sum(lu_le is not null or statut = 'LU') as lus,
                       sum(statut = 'ECHEC') as echecs,
                       sum(statut = 'A_ENVOYER') as enAttente,
                       sum(statut = 'EXCLU') as exclus
                from crm_campagne_message where campagne_id = :id
                """, p);
        long reponses = jdbc.queryForObject("""
                select count(distinct r.id) from crm_message_recu r join crm_campagne_message m on m.id = r.campagne_message_id
                where m.campagne_id = :id
                """, p, Long.class);
        long envoyes = nombre(c.get("envoyes"));
        long distribues = nombre(c.get("distribues"));
        long lus = nombre(c.get("lus"));
        long echecs = nombre(c.get("echecs"));
        return AnalyseCampagneDTO.builder()
                .cibles(nombre(c.get("cibles"))).envoyes(envoyes).distribues(distribues).lus(lus).echecs(echecs)
                .enAttente(nombre(c.get("enAttente"))).exclus(nombre(c.get("exclus"))).reponses(reponses)
                .tauxDistribution(taux(distribues, envoyes)).tauxLecture(taux(lus, distribues))
                .tauxEchec(taux(echecs, envoyes + echecs)).tauxReponse(taux(reponses, envoyes))
                .courbe(courbe(p))
                .build();
    }

    /** Envoyes, distribues, lus, echecs et reponses par jour (heure d'Abidjan = UTC). */
    private List<PointEvolutionDTO> courbe(Map<String, Object> p) {
        TreeMap<LocalDate, PointEvolutionDTO> jours = new TreeMap<>();
        jdbc.query("""
                select date(m.envoye_le) as jour, 'E' as quoi, count(*) as n from crm_campagne_message m where m.campagne_id = :id and m.envoye_le is not null group by 1
                union all select date(m.distribue_le), 'D', count(*) from crm_campagne_message m where m.campagne_id = :id and m.distribue_le is not null group by 1
                union all select date(m.lu_le), 'L', count(*) from crm_campagne_message m where m.campagne_id = :id and m.lu_le is not null group by 1
                union all select date(m.updated_at), 'X', count(*) from crm_campagne_message m where m.campagne_id = :id and m.statut = 'ECHEC' group by 1
                union all select date(r.recu_le), 'R', count(distinct r.id) from crm_message_recu r join crm_campagne_message m on m.id = r.campagne_message_id
                  where m.campagne_id = :id group by 1
                """, p, rs -> {
            PointEvolutionDTO point = jours.computeIfAbsent(rs.getDate(1).toLocalDate(), d -> PointEvolutionDTO.builder().date(d).build());
            long n = rs.getLong(3);
            switch (rs.getString(2)) {
                case "E" -> point.setEnvoyes(n);
                case "D" -> point.setDistribues(n);
                case "L" -> point.setLus(n);
                case "X" -> point.setEchecs(n);
                default -> point.setReponses(n);
            }
        });
        if (jours.isEmpty() || jours.firstKey().plusDays(JOURS_MAX).isBefore(jours.lastKey())) {
            return new ArrayList<>(jours.values());
        }
        // Jours sans activite a zero, pour une courbe continue
        for (LocalDate d = jours.firstKey(); !d.isAfter(jours.lastKey()); d = d.plusDays(1)) {
            jours.computeIfAbsent(d, j -> PointEvolutionDTO.builder().date(j).build());
        }
        return new ArrayList<>(jours.values());
    }

    private static long nombre(Object valeur) {
        return valeur == null ? 0 : ((Number) valeur).longValue();
    }

    private static Double taux(long part, long total) {
        return total == 0 ? null : Math.round(part * 1000.0 / total) / 10.0;
    }
}
