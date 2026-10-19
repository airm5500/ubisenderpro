package ci.ubipharm.crm.rest.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.DesabonnementService;
import ci.ubipharm.crm.rest.service.EvenementWhatsappService;
import ci.ubipharm.crm.rest.service.dto.EvenementAccuseDTO;
import ci.ubipharm.crm.rest.service.dto.EvenementMessageDTO;
import ci.ubipharm.crm.rest.service.dto.EvenementStatutDTO;

@Service
public class EvenementWhatsappServiceImpl implements EvenementWhatsappService {

    private static final Set<String> STATUTS_SESSION = Set.of("DECONNECTE", "CONNEXION", "QR", "CONNECTE");

    /** Statuts de facture qu'un accuse ou une reponse peut faire avancer. */
    private static final String AVANT_DISTRIBUE = "('A_RELANCER', 'RELANCE_PROGRAMMEE', 'RELANCE_ENVOYEE')";
    private static final String AVANT_LU = "('A_RELANCER', 'RELANCE_PROGRAMMEE', 'RELANCE_ENVOYEE', 'MESSAGE_DISTRIBUE')";
    private static final String AVANT_REPONSE = "('A_RELANCER', 'RELANCE_PROGRAMMEE', 'RELANCE_ENVOYEE', 'MESSAGE_DISTRIBUE', 'MESSAGE_LU')";

    private final NamedParameterJdbcTemplate jdbc;
    private final DesabonnementService desabonnementService;
    private final byte[] jeton;

    public EvenementWhatsappServiceImpl(NamedParameterJdbcTemplate jdbc, DesabonnementService desabonnementService,
                                        @Value("${crm.whatsapp.jeton:}") String jeton) {
        this.jdbc = jdbc;
        this.desabonnementService = desabonnementService;
        this.jeton = jeton.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void verifierJeton(String recu) {
        if (jeton.length == 0 || recu == null || !MessageDigest.isEqual(jeton, recu.getBytes(StandardCharsets.UTF_8))) {
            throw new BadCredentialsException("Jeton du service WhatsApp invalide");
        }
    }

    @Override
    @Transactional
    public void statut(EvenementStatutDTO e) {
        if (e.getSessionId() == null || !STATUTS_SESSION.contains(e.getStatus())) {
            return;
        }
        jdbc.update("""
                insert into crm_session_whatsapp (numero, statut, sante, raison, connecte_le, updated_at)
                values (:numero, :statut, :sante, :raison, case when :statut = 'CONNECTE' then utc_timestamp() end, utc_timestamp())
                on duplicate key update
                    connecte_le = case when values(statut) = 'CONNECTE' and statut <> 'CONNECTE' then utc_timestamp() else connecte_le end,
                    statut = values(statut), sante = values(sante), raison = values(raison), updated_at = utc_timestamp()
                """, new MapSqlParameterSource("numero", e.getSessionId())
                .addValue("statut", e.getStatus())
                .addValue("sante", "DEGRADED".equals(e.getHealth()) ? "DEGRADED" : "OK")
                .addValue("raison", e.getReason()));
    }

    @Override
    @Transactional
    public void accuse(EvenementAccuseDTO e) {
        MapSqlParameterSource p = new MapSqlParameterSource("numero", e.getSessionId()).addValue("identifiant", e.getId());
        if ("DISTRIBUE".equals(e.getStatus())) {
            jdbc.update("""
                    update crm_campagne_message set statut = 'DISTRIBUE', distribue_le = coalesce(distribue_le, utc_timestamp()), updated_at = utc_timestamp()
                    where numero_expediteur = :numero and identifiant_whatsapp = :identifiant and statut = 'ENVOYE'
                    """, p);
            avancerFacture(p, "MESSAGE_DISTRIBUE", AVANT_DISTRIBUE);
        } else if ("LU".equals(e.getStatus())) {
            lu(p);
        }
    }

    @Override
    @Transactional
    public void message(EvenementMessageDTO e) {
        if (e.getSessionId() == null || e.getId() == null || e.getFrom() == null) {
            return;
        }
        String numeroClient = e.getFrom().replaceAll("\\D", "");
        List<Long> clients = jdbc.queryForList("""
                select ct.client_id from crm_contact ct join crm_client c on c.id = ct.client_id
                where ct.numero_whatsapp = :numero and ct.statut = 'ACTIF' and c.statut = 'ACTIF'
                order by ct.fonction = 'PRINCIPAL' desc, ct.rang, ct.id limit 1
                """, Map.of("numero", numeroClient), Long.class);
        List<Map<String, Object>> relances = jdbc.queryForList("""
                select id, identifiant_whatsapp from crm_campagne_message
                where numero_expediteur = :agent and numero_destinataire = :client and statut in ('ENVOYE', 'DISTRIBUE', 'LU')
                order by envoye_le desc, id desc limit 1
                """, Map.of("agent", e.getSessionId(), "client", numeroClient));
        Long relance = relances.isEmpty() ? null : ((Number) relances.getFirst().get("id")).longValue();
        int nouveaux = jdbc.update("""
                insert ignore into crm_message_recu (numero_agent, numero_client, nom_affiche, client_id, campagne_message_id, type, contenu,
                                                     identifiant_whatsapp, recu_le)
                values (:agent, :client, :nom, :clientId, :relance, :type, :contenu, :identifiant, utc_timestamp())
                """, new MapSqlParameterSource("agent", e.getSessionId())
                .addValue("client", numeroClient)
                .addValue("nom", e.getName())
                .addValue("clientId", clients.isEmpty() ? null : clients.getFirst())
                .addValue("relance", relance)
                .addValue("type", e.getType() == null ? "TEXTE" : e.getType())
                .addValue("contenu", e.getText())
                .addValue("identifiant", e.getId()));
        if (nouveaux > 0 && !clients.isEmpty()) {
            desabonnementService.traiterReponse(clients.getFirst(), e.getText());
        }
        if (nouveaux == 0 || relance == null) {
            return;
        }
        // Une reponse vaut lecture de la relance
        MapSqlParameterSource p = new MapSqlParameterSource("numero", e.getSessionId())
                .addValue("identifiant", relances.getFirst().get("identifiant_whatsapp"));
        lu(p);
        avancerFacture(p, "REPONSE_RECUE", AVANT_REPONSE);
    }

    private void lu(MapSqlParameterSource p) {
        jdbc.update("""
                update crm_campagne_message set statut = 'LU', distribue_le = coalesce(distribue_le, utc_timestamp()),
                       lu_le = coalesce(lu_le, utc_timestamp()), updated_at = utc_timestamp()
                where numero_expediteur = :numero and identifiant_whatsapp = :identifiant and statut in ('ENVOYE', 'DISTRIBUE')
                """, p);
        avancerFacture(p, "MESSAGE_LU", AVANT_LU);
    }

    private void avancerFacture(MapSqlParameterSource p, String statut, String statutsPrecedents) {
        jdbc.update("""
                update crm_facture f join crm_campagne_message m on m.facture_id = f.id
                set f.statut = '%s', f.updated_at = utc_timestamp()
                where m.numero_expediteur = :numero and m.identifiant_whatsapp = :identifiant and f.statut in %s
                """.formatted(statut, statutsPrecedents), p);
    }
}
