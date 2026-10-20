package ci.ubipharm.crm.socle.support;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import ci.ubipharm.crm.rest.service.ParametreService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.extern.slf4j.Slf4j;

/**
 * E-mail a support.email pour chaque ticket ouvert automatiquement : le support est prevenu avant que l'utilisateur n'appelle.
 * Envoye en arriere-plan ; sans adresse ou sans serveur SMTP, rien ne part et rien n'echoue.
 */
@Slf4j
@Component
public class NotificationSupport {

    private final TachesSupport taches;
    private final ObjectProvider<JavaMailSender> serveur;
    private final ParametreService parametreService;
    private final EntityManager em;
    private final String compteSmtp;

    public NotificationSupport(TachesSupport taches, ObjectProvider<JavaMailSender> serveur, ParametreService parametreService, EntityManager em,
            @Value("${spring.mail.username:}") String compteSmtp) {
        this.taches = taches;
        this.serveur = serveur;
        this.parametreService = parametreService;
        this.em = em;
        this.compteSmtp = compteSmtp;
    }

    public void ticketAutomatique(Long ticketId) {
        taches.lancer(() -> envoyer(ticketId));
    }

    void envoyer(Long ticketId) {
        String destinataire = parametreService.valeur("support.email", "");
        JavaMailSender envoi = serveur.getIfAvailable();
        if (destinataire.isBlank() || envoi == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select t.sujet as sujet, (select m.contenu from crm_support_message m where m.ticket_id = t.id order by m.id limit 1) as contenu
                from crm_support_ticket t where t.id = :id
                """, Tuple.class).setParameter("id", ticketId).getResultList();
        if (lignes.isEmpty()) {
            return;
        }
        String expediteur = parametreService.valeur("email.expediteur", compteSmtp);
        String entreprise = parametreService.valeur("app.nom", "UBIPHARM CRM");
        SimpleMailMessage courrier = new SimpleMailMessage();
        if (expediteur != null && !expediteur.isBlank()) {
            courrier.setFrom(expediteur);
        }
        courrier.setTo(destinataire);
        courrier.setSubject("[" + entreprise + "] Ticket n° " + ticketId + " " + lignes.getFirst().get("sujet", String.class));
        courrier.setText(lignes.getFirst().get("contenu", String.class));
        try {
            envoi.send(courrier);
        } catch (RuntimeException e) {
            log.warn("E-mail du ticket automatique {} non envoye : {}", ticketId, e.getMessage());
        }
    }
}
