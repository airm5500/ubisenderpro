package ci.ubipharm.crm.rest.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.SuiviEnvoiService;
import ci.ubipharm.crm.rest.service.EnvoiSmsService;
import ci.ubipharm.crm.rest.service.dto.EtatSmsDTO;
import ci.ubipharm.crm.rest.service.impl.PasserelleSmsHttp.Configuration;
import ci.ubipharm.crm.rest.service.impl.PasserelleSmsHttp.Reponse;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * Relances par SMS : elles partent par le fournisseur par defaut, une a une, au numero principal du client, sous le nom
 * d'expediteur du fournisseur. Le statut distribue arrive par l'accuse du fournisseur, ou en l'interrogeant.
 */
@Slf4j
@Service
public class EnvoiSmsServiceImpl implements EnvoiSmsService {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");
    private static final String AVANT_DISTRIBUE = "('A_RELANCER', 'RELANCE_PROGRAMMEE', 'RELANCE_ENVOYEE')";

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ParametreService parametreService;
    private final SuiviEnvoiService suiviEnvoi;
    private final PasserelleSmsHttp passerelle;

    private final AtomicReference<Instant> prochainEnvoi = new AtomicReference<>(Instant.EPOCH);
    private final AtomicReference<String> alerte = new AtomicReference<>();

    private record Message(Long id, Long campagneId, Long factureId, String numero, String contenu, int tentatives) {
    }

    public EnvoiSmsServiceImpl(NamedParameterJdbcTemplate jdbc, TransactionTemplate transaction, ParametreService parametreService,
            SuiviEnvoiService suiviEnvoi, PasserelleSmsHttp passerelle) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.parametreService = parametreService;
        this.suiviEnvoi = suiviEnvoi;
        this.passerelle = passerelle;
    }

    @Override
    public int envoyerSuivant() {
        if (prochainEnvoi.get().isAfter(Instant.now())) {
            return 0;
        }
        Configuration fournisseur = passerelle.parDefaut();
        if (fournisseur == null || !fournisseur.pret()) {
            return 0;
        }
        int heure = ZonedDateTime.now(ABIDJAN).getHour();
        if (heure < parametreService.entier("sms.envoi.heure_debut", 8) || heure >= parametreService.entier("sms.envoi.heure_fin", 18)) {
            return 0;
        }
        if (envoyesAujourdhui() >= parametreService.entier("sms.envoi.plafond_jour", 2000)) {
            return 0;
        }
        Message message = reserver(null);
        if (message == null) {
            return 0;
        }
        Reponse reponse = passerelle.envoyer(fournisseur, message.numero(), message.contenu(), "CRM-" + message.id());
        transaction.executeWithoutResult(statut -> enregistrer(fournisseur, message, reponse));
        // Cle refusee : on attend sa correction avant d'essayer le message suivant
        Duration attente = reponse.accesRefuse() ? Duration.ofMinutes(parametreService.entier("sms.envoi.pause_cle_refusee_minutes", 10))
                : Duration.ofSeconds(parametreService.entier("sms.envoi.delai_secondes", 1));
        prochainEnvoi.set(Instant.now().plus(attente));
        return reponse.reussie() ? 1 : 0;
    }

    @Override
    public boolean envoyerMaintenant(Long messageId) {
        Configuration fournisseur = passerelle.parDefaut();
        if (fournisseur == null || !fournisseur.pret()) {
            throw new ci.ubipharm.crm.socle.erreur.ErreurMetierException("Aucun fournisseur SMS prêt : renseignez sa clé dans Paramètres > Fournisseurs SMS.");
        }
        Message message = reserver(messageId);
        if (message == null) {
            return false;
        }
        Reponse reponse = passerelle.envoyer(fournisseur, message.numero(), message.contenu(), "CRM-" + message.id());
        transaction.executeWithoutResult(statut -> enregistrer(fournisseur, message, reponse));
        return reponse.reussie();
    }

    @Override
    public int reprendreEnvoisInterrompus() {
        return transaction.execute(statut -> {
            List<Long> campagnes = jdbc.queryForList("select distinct campagne_id from crm_campagne_message where statut = 'ENVOI' and canal = 'SMS'",
                    Map.of(), Long.class);
            int interrompus = jdbc.update("""
                    update crm_campagne_message set statut = 'ECHEC', updated_at = utc_timestamp(),
                           motif = 'Envoi interrompu : vérifiez l''historique du fournisseur SMS avant de relancer'
                    where statut = 'ENVOI' and canal = 'SMS'
                    """, Map.of());
            campagnes.forEach(suiviEnvoi::recompter);
            return interrompus;
        });
    }

    @Override
    public int verifierStatuts() {
        List<Map<String, Object>> envoyes = jdbc.queryForList("""
                select id, fournisseur_sms_id, identifiant_sms from crm_campagne_message
                where canal = 'SMS' and statut = 'ENVOYE' and identifiant_sms is not null
                  and envoye_le between utc_timestamp() - interval 2 day and utc_timestamp() - interval 5 minute
                order by updated_at, id limit 20
                """, Map.of());
        int misAJour = 0;
        for (Map<String, Object> envoye : envoyes) {
            Long id = ((Number) envoye.get("id")).longValue();
            Configuration fournisseur = passerelle.parId(((Number) envoye.get("fournisseur_sms_id")).longValue());
            String statut = fournisseur == null ? null : passerelle.statut(fournisseur, (String) envoye.get("identifiant_sms"));
            Integer change = transaction.execute(s -> {
                // Le message repasse en fin de file meme sans nouveau statut, pour que tous soient interroges a tour de role
                jdbc.update("update crm_campagne_message set updated_at = utc_timestamp() where id = :id", Map.of("id", id));
                return statut == null ? 0 : appliquer(id, statut);
            });
            misAJour += change == null ? 0 : change;
        }
        return misAJour;
    }

    @Override
    public void accuse(String code, String jeton, String corps, Map<String, String> parametres) {
        Configuration fournisseur = passerelle.parCode(code);
        if (fournisseur == null || jeton == null
                || !MessageDigest.isEqual(fournisseur.jetonAccuse().getBytes(StandardCharsets.UTF_8), jeton.getBytes(StandardCharsets.UTF_8))) {
            throw new BadCredentialsException("Jeton d'accusé SMS invalide");
        }
        JsonNode json = passerelle.analyser(corps);
        String identifiant = PasserelleSmsHttp.lireAccuse(json, parametres, fournisseur.champIdentifiantAccuse());
        String statut = PasserelleSmsHttp.correspondance(fournisseur, PasserelleSmsHttp.lireAccuse(json, parametres, fournisseur.champStatutAccuse()));
        if (identifiant == null || statut == null) {
            return;
        }
        transaction.executeWithoutResult(s -> {
            List<Long> ids = jdbc.queryForList("""
                    select id from crm_campagne_message where canal = 'SMS' and fournisseur_sms_id = :fournisseur and identifiant_sms = :identifiant
                    """, new MapSqlParameterSource("fournisseur", fournisseur.id()).addValue("identifiant", identifiant), Long.class);
            ids.forEach(id -> appliquer(id, statut));
        });
    }

    @Override
    public EtatSmsDTO etat() {
        Configuration fournisseur = passerelle.parDefaut();
        Long enAttente = jdbc.queryForObject("""
                select count(*) from crm_campagne_message m join crm_campagne c on c.id = m.campagne_id
                where m.canal = 'SMS' and m.statut = 'A_ENVOYER' and c.statut = 'EN_COURS'
                """, Map.of(), Long.class);
        return EtatSmsDTO.builder()
                .configure(fournisseur != null && fournisseur.pret())
                .fournisseur(fournisseur == null ? null : fournisseur.libelle())
                .expediteur(fournisseur == null ? null : fournisseur.expediteur())
                .enAttente(enAttente == null ? 0 : enAttente)
                .envoyesAujourdhui(envoyesAujourdhui())
                .heureDebut(parametreService.entier("sms.envoi.heure_debut", 8))
                .heureFin(parametreService.entier("sms.envoi.heure_fin", 18))
                .alerte(alerte.get())
                .build();
    }

    /** Passe le plus ancien SMS en attente (ou celui-ci) a ENVOI ; null s'il n'y en a pas. */
    private Message reserver(Long id) {
        return transaction.execute(statut -> {
            List<Message> messages = jdbc.query("""
                    select m.id, m.campagne_id, m.facture_id, m.numero_destinataire, m.contenu, m.tentatives
                    from crm_campagne_message m join crm_campagne c on c.id = m.campagne_id
                    where m.canal = 'SMS' and m.statut = 'A_ENVOYER' and c.statut = 'EN_COURS'""" + (id == null ? "" : " and m.id = :id") + """

                    order by c.lance_le, m.id limit 1 for update
                    """, id == null ? Map.of() : Map.of("id", id), (rs, i) -> new Message(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5), rs.getInt(6)));
            if (messages.isEmpty()) {
                return null;
            }
            jdbc.update("update crm_campagne_message set statut = 'ENVOI', updated_at = utc_timestamp() where id = :id",
                    Map.of("id", messages.getFirst().id()));
            return messages.getFirst();
        });
    }

    private void enregistrer(Configuration fournisseur, Message message, Reponse reponse) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", message.id())
                .addValue("fournisseur", fournisseur.id())
                .addValue("identifiant", reponse.identifiant())
                .addValue("nbSms", reponse.nbSms());
        if (reponse.reussie()) {
            alerte.set(null);
            jdbc.update("""
                    update crm_campagne_message set statut = 'ENVOYE', envoye_le = utc_timestamp(), tentatives = tentatives + 1, motif = null,
                           fournisseur_sms_id = :fournisseur, identifiant_sms = :identifiant, nb_sms = :nbSms, updated_at = utc_timestamp()
                    where id = :id
                    """, p);
            suiviEnvoi.factureRelancee(message.factureId());
        } else if (reponse.accesRefuse()) {
            // La cle est en cause, pas le message : il reprend sa place sans compter d'essai
            alerte.set("Le fournisseur " + fournisseur.libelle() + " refuse la clé API : les SMS attendent sa correction.");
            log.warn("Fournisseur SMS {} : acces refuse ({})", fournisseur.code(), reponse.statutHttp());
            jdbc.update("update crm_campagne_message set statut = 'A_ENVOYER', motif = 'Clé du fournisseur SMS refusée', updated_at = utc_timestamp() "
                    + "where id = :id", p);
        } else if (reponse.passagere() && message.tentatives() + 1 < parametreService.entier("whatsapp.envoi.tentatives_max", 3)) {
            jdbc.update("update crm_campagne_message set statut = 'A_ENVOYER', tentatives = tentatives + 1, motif = :motif, "
                    + "updated_at = utc_timestamp() where id = :id", p.addValue("motif", motifPassager(reponse)));
        } else {
            String motif = reponse.passagere() ? motifPassager(reponse) : "Refusé par le fournisseur : " + reponse.erreur();
            jdbc.update("update crm_campagne_message set statut = 'ECHEC', tentatives = tentatives + 1, motif = :motif, fournisseur_sms_id = :fournisseur, "
                    + "updated_at = utc_timestamp() where id = :id", p.addValue("motif", motif.length() > 250 ? motif.substring(0, 250) : motif));
            suiviEnvoi.factureNonRelancee(message.factureId());
        }
        suiviEnvoi.recompter(message.campagneId());
    }

    private static String motifPassager(Reponse reponse) {
        return reponse.injoignable() ? "Fournisseur SMS injoignable" : "Fournisseur SMS en erreur (" + reponse.statutHttp() + ")";
    }

    /** Statut remonte par le fournisseur ; il n'avance que depuis ENVOYE, sans jamais reculer. */
    private int appliquer(Long id, String statut) {
        Map<String, Object> m = Map.of("id", id);
        if ("DISTRIBUE".equals(statut)) {
            int n = jdbc.update("""
                    update crm_campagne_message set statut = 'DISTRIBUE', distribue_le = coalesce(distribue_le, utc_timestamp()), updated_at = utc_timestamp()
                    where id = :id and statut = 'ENVOYE'
                    """, m);
            jdbc.update("""
                    update crm_facture f join crm_campagne_message cm on cm.facture_id = f.id
                    set f.statut = 'MESSAGE_DISTRIBUE', f.updated_at = utc_timestamp()
                    where cm.id = :id and f.statut in %s
                    """.formatted(AVANT_DISTRIBUE), m);
            return n;
        }
        if ("ECHEC".equals(statut)) {
            int n = jdbc.update("""
                    update crm_campagne_message set statut = 'ECHEC', motif = 'Non remis par l''opérateur', updated_at = utc_timestamp()
                    where id = :id and statut = 'ENVOYE'
                    """, m);
            if (n > 0) {
                // La facture n'a pas ete relancee : elle redevient a relancer si aucun autre message ne l'a atteinte
                jdbc.update("""
                        update crm_facture f join crm_campagne_message cm on cm.facture_id = f.id
                        set f.statut = 'A_RELANCER', f.updated_at = utc_timestamp()
                        where cm.id = :id and f.statut = 'RELANCE_ENVOYEE'
                          and not exists (select 1 from crm_campagne_message x where x.facture_id = f.id and x.id <> cm.id
                                          and x.statut in ('A_ENVOYER', 'ENVOI', 'ENVOYE', 'DISTRIBUE', 'LU'))
                        """, m);
                Long campagne = jdbc.queryForObject("select campagne_id from crm_campagne_message where id = :id", m, Long.class);
                suiviEnvoi.recompter(campagne);
            }
            return n;
        }
        return 0;
    }

    private long envoyesAujourdhui() {
        Long n = jdbc.queryForObject("select count(*) from crm_campagne_message where canal = 'SMS' and envoye_le >= utc_date()", Map.of(), Long.class);
        return n == null ? 0 : n;
    }
}
