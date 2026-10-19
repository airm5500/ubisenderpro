package ci.ubipharm.crm.rest.service.impl;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import ci.ubipharm.crm.rest.service.SuiviEnvoiService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SuiviEnvoiServiceImpl implements SuiviEnvoiService {

    /** Statuts de facture que l'envoi fait avancer ; les suivants (distribue, lu, reponse...) ne reculent jamais. */
    private static final String AVANT_ENVOI = "('A_RELANCER', 'RELANCE_PROGRAMMEE')";

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void factureRelancee(Long factureId) {
        jdbc.update("update crm_facture set statut = 'RELANCE_ENVOYEE', updated_at = utc_timestamp() where id = :factureId and statut in "
                + AVANT_ENVOI, Map.of("factureId", factureId));
    }

    @Override
    public void factureNonRelancee(Long factureId) {
        jdbc.update("""
                update crm_facture f set f.statut = 'A_RELANCER', f.updated_at = utc_timestamp()
                where f.id = :factureId and f.statut = 'RELANCE_PROGRAMMEE'
                  and not exists (select 1 from crm_campagne_message m where m.facture_id = f.id and m.statut in ('A_ENVOYER', 'ENVOI'))
                """, Map.of("factureId", factureId));
    }

    /**
     * MariaDB applique les affectations dans l'ordre : termine_le est calcule avant que statut ne change. Une campagne
     * automatique dont l'envoi est fini attend le suivant (VALIDEE) au lieu de se terminer.
     */
    @Override
    public void recompter(Long campagneId) {
        jdbc.update("""
                update crm_campagne c set
                    nb_envoyes = (select count(*) from crm_campagne_message m where m.campagne_id = c.id and m.statut in ('ENVOYE', 'DISTRIBUE', 'LU')),
                    nb_echecs = (select count(*) from crm_campagne_message m where m.campagne_id = c.id and m.statut = 'ECHEC'),
                    termine_le = case when c.statut = 'EN_COURS' and (c.recurrence is null or c.date_execution <= utc_timestamp()) and not exists (select 1 from crm_campagne_message m
                                  where m.campagne_id = c.id and m.statut in ('A_ENVOYER', 'ENVOI')) then utc_timestamp() else c.termine_le end,
                    statut = case when c.statut = 'EN_COURS' and not exists (select 1 from crm_campagne_message m
                                  where m.campagne_id = c.id and m.statut in ('A_ENVOYER', 'ENVOI'))
                                  then case when c.recurrence is not null and c.date_execution > utc_timestamp() then 'VALIDEE' else 'TERMINEE' end
                                  else c.statut end
                where c.id = :id
                """, Map.of("id", campagneId));
    }
}
