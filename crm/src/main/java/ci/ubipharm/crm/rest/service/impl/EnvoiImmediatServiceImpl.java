package ci.ubipharm.crm.rest.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import ci.ubipharm.crm.rest.service.EnvoiEmailService;
import ci.ubipharm.crm.rest.service.EnvoiImmediatService;
import ci.ubipharm.crm.rest.service.EnvoiMasseService;
import ci.ubipharm.crm.rest.service.EnvoiSmsService;
import ci.ubipharm.crm.rest.service.EnvoiWhatsappService;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.dto.EnvoiImmediatDTO;
import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.rest.service.impl.ReglesCadence.Verdict;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EnvoiImmediatServiceImpl implements EnvoiImmediatService {

    private final NamedParameterJdbcTemplate jdbc;
    private final PerimetreService perimetreService;
    private final EnvoiWhatsappService envoiWhatsappService;
    private final EnvoiSmsService envoiSmsService;
    private final EnvoiEmailService envoiEmailService;
    private final EnvoiMasseService envoiMasseService;
    private final JournalActionService journalActionService;
    private final CadenceWhatsapp cadence;

    @Override
    public EnvoiImmediatDTO envoyer(String cle) {
        if (cle == null || !cle.matches("[CS]\\d{1,18}")) {
            throw new RessourceIntrouvableException("Message introuvable");
        }
        boolean campagne = cle.charAt(0) == 'C';
        Long id = Long.valueOf(cle.substring(1));
        List<Map<String, Object>> lignes = campagne
                ? jdbc.queryForList("""
                        select m.statut, m.canal, m.numero_expediteur, c.statut as parent, c.cree_par, m.agent_id
                        from crm_campagne_message m join crm_campagne c on c.id = m.campagne_id where m.id = :id
                        """, Map.of("id", id))
                : jdbc.queryForList("""
                        select s.statut, 'WHATSAPP' as canal, s.numero_expediteur, e.statut as parent, s.cree_par, null as agent_id
                        from crm_message_sortant s join crm_envoi_masse e on e.id = s.envoi_masse_id where s.id = :id
                        """, Map.of("id", id));
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Message introuvable");
        }
        Map<String, Object> m = lignes.getFirst();
        verifierDroit(m);
        if (!"A_ENVOYER".equals(m.get("statut"))) {
            throw new ErreurMetierException("Ce message n'est plus en attente : il ne peut pas être envoyé maintenant.");
        }
        if (!"EN_COURS".equals(m.get("parent"))) {
            throw new ErreurMetierException(campagne ? "La campagne n'est pas en cours : elle doit être validée et lancée avant d'envoyer ses messages."
                    : "Cet envoi en masse n'est plus en cours.");
        }
        String canal = (String) m.get("canal");
        if ("WHATSAPP".equals(canal) && jdbc.queryForObject("select count(*) from crm_session_whatsapp where numero = :numero and (statut = 'CONNECTE' or fournisseur = 'META')",
                Map.of("numero", String.valueOf(m.get("numero_expediteur"))), Integer.class) == 0) {
            throw new ErreurMetierException("Le numéro d'envoi n'est pas connecté : reliez le téléphone dans Numéros WhatsApp.");
        }
        if ("WHATSAPP".equals(canal)) {
            // Hors plage horaire, oui ; mais jamais en rafale : attente, pauses et plafonds du numero restent dus
            Verdict verdict = cadence.verdict(String.valueOf(m.get("numero_expediteur")));
            if (!verdict.autorise()) {
                throw new ErreurMetierException(verdict.motif() + " : ce numéro pourra envoyer " + reprise(verdict.reprise())
                        + ". Le message reste en attente et partira à son tour.");
            }
        }
        boolean parti = !campagne ? envoiMasseService.envoyerMaintenant(id) : switch (canal) {
            case "SMS" -> envoiSmsService.envoyerMaintenant(id);
            case "EMAIL" -> envoiEmailService.envoyerMaintenant(id);
            default -> envoiWhatsappService.envoyerMaintenant(id);
        };
        Map<String, Object> apres = jdbc.queryForMap(campagne ? "select statut, motif from crm_campagne_message where id = :id"
                : "select statut, motif from crm_message_sortant where id = :id", Map.of("id", id));
        journalActionService.enregistrer("ENVOI_IMMEDIAT", campagne ? "crm_campagne_message" : "crm_message_sortant", id,
                canal + (parti ? " : envoyé" : " : " + apres.get("statut")));
        return EnvoiImmediatDTO.builder().cle(cle).statut((String) apres.get("statut")).motif((String) apres.get("motif")).build();
    }

    /** « dans 25 s », « à 14 h 05 » ou « demain », heure d'Abidjan (UTC). */
    static String reprise(LocalDateTime reprise) {
        LocalDateTime maintenant = LocalDateTime.now(ZoneOffset.UTC);
        long secondes = Duration.between(maintenant, reprise).getSeconds();
        if (secondes < 60) {
            return "dans " + Math.max(1, secondes) + " s";
        }
        if (!reprise.toLocalDate().equals(maintenant.toLocalDate())) {
            return "demain";
        }
        return "à " + reprise.getHour() + " h " + String.format("%02d", reprise.getMinute());
    }

    /** Son auteur, l'expediteur du numero, ou une vision globale. */
    private void verifierDroit(Map<String, Object> m) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (perimetre.isVisionGlobale()) {
            return;
        }
        Long moi = perimetre.getUtilisateurId();
        boolean auteur = m.get("cree_par") != null && moi.equals(((Number) m.get("cree_par")).longValue());
        boolean agent = m.get("agent_id") != null && moi.equals(((Number) m.get("agent_id")).longValue());
        boolean numero = m.get("numero_expediteur") != null && jdbc.queryForObject(
                "select count(*) from crm_utilisateur where id = :moi and numero_whatsapp = :numero",
                Map.of("moi", moi, "numero", m.get("numero_expediteur")), Integer.class) > 0;
        if (!auteur && !agent && !numero) {
            throw new ErreurMetierException("Seul l'auteur, l'expéditeur ou un profil de pilotage peut envoyer ce message maintenant.");
        }
    }
}
