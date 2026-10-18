package ci.ubipharm.crm.email.rest.service.impl;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import ci.ubipharm.crm.commun.rest.service.ParametreService;
import ci.ubipharm.crm.commun.rest.service.SuiviEnvoiService;
import ci.ubipharm.crm.email.rest.service.EnvoiEmailService;
import ci.ubipharm.crm.email.rest.service.dto.EtatEmailDTO;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * Relances par e-mail : elles partent de l'adresse d'UBIPHARM, une a une, avec l'adresse de l'agent du client
 * en reponse (Reply-To) pour que le client lui reponde directement. Le serveur SMTP vient de spring.mail.*.
 */
@Slf4j
@Service
public class EnvoiEmailServiceImpl implements EnvoiEmailService {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectProvider<JavaMailSender> serveur;
    private final ParametreService parametreService;
    private final SuiviEnvoiService suiviEnvoi;
    private final String compteSmtp;

    private final AtomicReference<Instant> prochainEnvoi = new AtomicReference<>(Instant.EPOCH);

    private record Message(Long id, Long campagneId, Long factureId, String destinataire, String reponse, String objet, String contenu,
            int tentatives) {
    }

    private enum Issue { ENVOYE, A_REESSAYER, REFUSE }

    public EnvoiEmailServiceImpl(NamedParameterJdbcTemplate jdbc, TransactionTemplate transaction, ObjectProvider<JavaMailSender> serveur,
            ParametreService parametreService, SuiviEnvoiService suiviEnvoi, @Value("${spring.mail.username:}") String compteSmtp) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.serveur = serveur;
        this.parametreService = parametreService;
        this.suiviEnvoi = suiviEnvoi;
        this.compteSmtp = compteSmtp;
    }

    @Override
    public int envoyerSuivant() {
        JavaMailSender envoi = serveur.getIfAvailable();
        if (envoi == null || prochainEnvoi.get().isAfter(Instant.now())) {
            return 0;
        }
        int heure = ZonedDateTime.now(ABIDJAN).getHour();
        if (heure < parametreService.entier("email.envoi.heure_debut", 8) || heure >= parametreService.entier("email.envoi.heure_fin", 18)) {
            return 0;
        }
        if (envoyesAujourdhui() >= parametreService.entier("email.envoi.plafond_jour", 1000)) {
            return 0;
        }
        Message message = reserver();
        if (message == null) {
            return 0;
        }
        String erreur = null;
        Issue issue;
        try {
            envoi.send(composer(envoi, message));
            issue = Issue.ENVOYE;
        } catch (MailSendException e) {
            // Adresse refusee par le serveur : inutile de reessayer
            boolean refuse = e.getFailedMessages().values().stream().anyMatch(SendFailedException.class::isInstance);
            issue = refuse ? Issue.REFUSE : Issue.A_REESSAYER;
            erreur = refuse ? "Adresse refusée par le serveur de messagerie" : "Serveur de messagerie injoignable";
            log.warn("E-mail {} non envoye : {}", message.id(), e.getMessage());
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            issue = Issue.A_REESSAYER;
            erreur = "Serveur de messagerie en erreur";
            log.warn("E-mail {} non envoye : {}", message.id(), e.getMessage());
        }
        Issue issueFinale = issue;
        String motif = erreur;
        transaction.executeWithoutResult(statut -> enregistrer(message, issueFinale, motif));
        prochainEnvoi.set(Instant.now().plusSeconds(parametreService.entier("email.envoi.delai_secondes", 2)));
        return issue == Issue.ENVOYE ? 1 : 0;
    }

    @Override
    public int reprendreEnvoisInterrompus() {
        return transaction.execute(statut -> {
            List<Long> campagnes = jdbc.queryForList("select distinct campagne_id from crm_campagne_message where statut = 'ENVOI' and canal = 'EMAIL'",
                    Map.of(), Long.class);
            int interrompus = jdbc.update("""
                    update crm_campagne_message set statut = 'ECHEC', updated_at = utc_timestamp(),
                           motif = 'Envoi interrompu : vérifiez la boîte d''envoi avant de relancer'
                    where statut = 'ENVOI' and canal = 'EMAIL'
                    """, Map.of());
            campagnes.forEach(suiviEnvoi::recompter);
            return interrompus;
        });
    }

    @Override
    public EtatEmailDTO etat() {
        Long enAttente = jdbc.queryForObject("""
                select count(*) from crm_campagne_message m join crm_campagne c on c.id = m.campagne_id
                where m.canal = 'EMAIL' and m.statut = 'A_ENVOYER' and c.statut = 'EN_COURS'
                """, Map.of(), Long.class);
        return EtatEmailDTO.builder()
                .configure(serveur.getIfAvailable() != null)
                .expediteur(expediteur())
                .enAttente(enAttente == null ? 0 : enAttente)
                .envoyesAujourdhui(envoyesAujourdhui())
                .heureDebut(parametreService.entier("email.envoi.heure_debut", 8))
                .heureFin(parametreService.entier("email.envoi.heure_fin", 18))
                .build();
    }

    private MimeMessage composer(JavaMailSender envoi, Message message) throws MessagingException, UnsupportedEncodingException {
        MimeMessage mime = envoi.createMimeMessage();
        MimeMessageHelper courrier = new MimeMessageHelper(mime, false, "UTF-8");
        String expediteur = expediteur();
        String nom = parametreService.valeur("email.nom_expediteur", "");
        if (nom.isBlank()) {
            courrier.setFrom(expediteur);
        } else {
            courrier.setFrom(expediteur, nom);
        }
        courrier.setTo(message.destinataire());
        if (message.reponse() != null && !message.reponse().isBlank()) {
            courrier.setReplyTo(message.reponse());
        }
        courrier.setSubject(message.objet());
        courrier.setText(message.contenu(), false);
        return mime;
    }

    /** Passe le plus ancien e-mail en attente a ENVOI ; null s'il n'y en a pas. */
    private Message reserver() {
        return transaction.execute(statut -> {
            List<Message> messages = jdbc.query("""
                    select m.id, m.campagne_id, m.facture_id, m.email_destinataire, m.email_reponse, m.objet, m.contenu, m.tentatives
                    from crm_campagne_message m join crm_campagne c on c.id = m.campagne_id
                    where m.canal = 'EMAIL' and m.statut = 'A_ENVOYER' and c.statut = 'EN_COURS'
                    order by c.lance_le, m.id limit 1 for update
                    """, Map.of(), (rs, i) -> new Message(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5),
                    rs.getString(6), rs.getString(7), rs.getInt(8)));
            if (messages.isEmpty()) {
                return null;
            }
            jdbc.update("update crm_campagne_message set statut = 'ENVOI', updated_at = utc_timestamp() where id = :id",
                    Map.of("id", messages.getFirst().id()));
            return messages.getFirst();
        });
    }

    private void enregistrer(Message message, Issue issue, String motif) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", message.id()).addValue("motif", motif);
        if (issue == Issue.ENVOYE) {
            jdbc.update("""
                    update crm_campagne_message set statut = 'ENVOYE', envoye_le = utc_timestamp(), tentatives = tentatives + 1, motif = null,
                           updated_at = utc_timestamp()
                    where id = :id
                    """, p);
            suiviEnvoi.factureRelancee(message.factureId());
        } else if (issue == Issue.A_REESSAYER && message.tentatives() + 1 < parametreService.entier("whatsapp.envoi.tentatives_max", 3)) {
            jdbc.update("update crm_campagne_message set statut = 'A_ENVOYER', tentatives = tentatives + 1, motif = :motif, "
                    + "updated_at = utc_timestamp() where id = :id", p);
        } else {
            jdbc.update("update crm_campagne_message set statut = 'ECHEC', tentatives = tentatives + 1, motif = :motif, "
                    + "updated_at = utc_timestamp() where id = :id", p);
            suiviEnvoi.factureNonRelancee(message.factureId());
        }
        suiviEnvoi.recompter(message.campagneId());
    }

    private long envoyesAujourdhui() {
        Long n = jdbc.queryForObject("select count(*) from crm_campagne_message where canal = 'EMAIL' and envoye_le >= utc_date()", Map.of(), Long.class);
        return n == null ? 0 : n;
    }

    private String expediteur() {
        String parametre = parametreService.valeur("email.expediteur", "");
        return parametre.isBlank() ? compteSmtp : parametre.trim();
    }
}
