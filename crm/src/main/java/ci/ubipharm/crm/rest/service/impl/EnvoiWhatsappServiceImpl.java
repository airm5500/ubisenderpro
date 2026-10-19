package ci.ubipharm.crm.rest.service.impl;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.SuiviEnvoiService;
import ci.ubipharm.crm.rest.service.EnvoiWhatsappService;
import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.rest.service.dto.ResultatEnvoiDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnvoiWhatsappServiceImpl implements EnvoiWhatsappService {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PasserelleWhatsappService passerelle;
    private final ParametreService parametreService;
    private final SuiviEnvoiService suiviEnvoi;

    /** Prochain envoi autorise par numero d'agent. */
    private final Map<String, Instant> prochainEnvoi = new ConcurrentHashMap<>();

    private record Message(Long id, Long campagneId, Long factureId, String numeroExpediteur, String numeroDestinataire, String contenu,
            int tentatives) {
    }

    @Override
    public int envoyerLot() {
        int heure = ZonedDateTime.now(ABIDJAN).getHour();
        if (heure < parametreService.entier("whatsapp.envoi.heure_debut", 8) || heure >= parametreService.entier("whatsapp.envoi.heure_fin", 18)) {
            return 0;
        }
        int plafond = parametreService.entier("whatsapp.envoi.plafond_jour", 200);
        List<String> numeros = jdbc.queryForList("""
                select distinct m.numero_expediteur from crm_campagne_message m
                join crm_campagne c on c.id = m.campagne_id
                join crm_session_whatsapp s on s.numero = m.numero_expediteur and s.statut = 'CONNECTE'
                where m.statut = 'A_ENVOYER' and c.statut = 'EN_COURS'
                """, Map.of(), String.class);
        int envoyes = 0;
        for (String numero : numeros) {
            Instant prochain = prochainEnvoi.get(numero);
            if (prochain != null && prochain.isAfter(Instant.now())) {
                continue;
            }
            Long aujourdhui = jdbc.queryForObject("""
                    select count(*) from crm_campagne_message where numero_expediteur = :numero and envoye_le >= utc_date()
                    """, Map.of("numero", numero), Long.class);
            if (aujourdhui != null && aujourdhui >= plafond) {
                continue;
            }
            Message message = reserver(numero);
            if (message == null) {
                continue;
            }
            ResultatEnvoiDTO resultat = passerelle.envoyerTexte(numero, message.numeroDestinataire(), message.contenu());
            transaction.executeWithoutResult(statut -> enregistrer(message, resultat));
            prochainEnvoi.put(numero, Instant.now().plusSeconds(attente()));
            envoyes++;
        }
        return envoyes;
    }

    @Override
    public int reprendreEnvoisInterrompus() {
        return transaction.execute(statut -> {
            List<Long> campagnes = jdbc.queryForList("select distinct campagne_id from crm_campagne_message where statut = 'ENVOI' and canal = 'WHATSAPP'", Map.of(), Long.class);
            int interrompus = jdbc.update("""
                    update crm_campagne_message set statut = 'ECHEC', updated_at = utc_timestamp(),
                           motif = 'Envoi interrompu : verifiez dans le WhatsApp de l''agent avant de relancer'
                    where statut = 'ENVOI' and canal = 'WHATSAPP'
                    """, Map.of());
            campagnes.forEach(suiviEnvoi::recompter);
            return interrompus;
        });
    }

    /** Passe le plus ancien message en attente du numero a ENVOI ; null si un autre passage l'a pris. */
    private Message reserver(String numero) {
        return transaction.execute(statut -> {
            List<Message> messages = jdbc.query("""
                    select m.id, m.campagne_id, m.facture_id, m.numero_expediteur, m.numero_destinataire, m.contenu, m.tentatives
                    from crm_campagne_message m join crm_campagne c on c.id = m.campagne_id
                    where m.numero_expediteur = :numero and m.statut = 'A_ENVOYER' and c.statut = 'EN_COURS'
                    order by c.lance_le, m.id limit 1 for update
                    """, Map.of("numero", numero), (rs, i) -> new Message(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                    rs.getString(5), rs.getString(6), rs.getInt(7)));
            if (messages.isEmpty()) {
                return null;
            }
            jdbc.update("update crm_campagne_message set statut = 'ENVOI', updated_at = utc_timestamp() where id = :id",
                    Map.of("id", messages.getFirst().id()));
            return messages.getFirst();
        });
    }

    private void enregistrer(Message message, ResultatEnvoiDTO resultat) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", message.id())
                .addValue("identifiant", resultat.getIdentifiant())
                .addValue("motif", resultat.getErreur());
        if (resultat.isSucces()) {
            jdbc.update("""
                    update crm_campagne_message set statut = 'ENVOYE', identifiant_whatsapp = :identifiant, envoye_le = utc_timestamp(),
                           tentatives = tentatives + 1, motif = null, updated_at = utc_timestamp()
                    where id = :id
                    """, p);
            suiviEnvoi.factureRelancee(message.factureId());
        } else if (resultat.isSessionIndisponible()) {
            // Le message attend la reconnexion du telephone de l'agent, sans compter d'essai
            jdbc.update("update crm_campagne_message set statut = 'A_ENVOYER', updated_at = utc_timestamp() where id = :id", p);
            jdbc.update("update crm_session_whatsapp set statut = 'DECONNECTE', updated_at = utc_timestamp() where numero = :numero",
                    Map.of("numero", message.numeroExpediteur()));
        } else if (resultat.isErreurTechnique() && message.tentatives() + 1 < parametreService.entier("whatsapp.envoi.tentatives_max", 3)) {
            jdbc.update("update crm_campagne_message set statut = 'A_ENVOYER', tentatives = tentatives + 1, motif = :motif, "
                    + "updated_at = utc_timestamp() where id = :id", p);
        } else {
            jdbc.update("update crm_campagne_message set statut = 'ECHEC', tentatives = tentatives + 1, motif = :motif, "
                    + "updated_at = utc_timestamp() where id = :id", p);
            suiviEnvoi.factureNonRelancee(message.factureId());
        }
        suiviEnvoi.recompter(message.campagneId());
    }

    private long attente() {
        int min = parametreService.entier("whatsapp.envoi.delai_min_secondes", 20);
        int max = Math.max(min, parametreService.entier("whatsapp.envoi.delai_max_secondes", 45));
        return min == max ? min : ThreadLocalRandom.current().nextLong(min, max + 1L);
    }
}
