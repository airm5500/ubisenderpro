package com.ubisenderpro.service;

import com.ubisenderpro.entity.Client;
import com.ubisenderpro.entity.ClientContact;
import com.ubisenderpro.entity.Message;
import com.ubisenderpro.entity.RecEnvoi;
import com.ubisenderpro.entity.RecModele;
import com.ubisenderpro.entity.WhatsappAccount;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Envoi d'une relance (WhatsApp texte ou Email) à un client, avec personnalisation
 * des variables finance et trace dans l'historique. Réutilise le moteur de
 * communication existant (WhatsappService, MailService).
 */
@Stateless
public class RecEnvoiService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private RecModeleService modeleService;
    @EJB
    private RecVariablesService variablesService;
    @EJB
    private WhatsappService whatsappService;
    @EJB
    private MailService mailService;

    public List<RecEnvoi> historique(Long clientId) {
        if (clientId != null) {
            return em.createQuery("SELECT e FROM RecEnvoi e WHERE e.clientId = :c ORDER BY e.createdAt DESC", RecEnvoi.class)
                    .setParameter("c", clientId).setMaxResults(500).getResultList();
        }
        return em.createQuery("SELECT e FROM RecEnvoi e ORDER BY e.createdAt DESC", RecEnvoi.class)
                .setMaxResults(500).getResultList();
    }

    /** Envoie une relance et trace l'historique. canal = WHATSAPP | EMAIL. */
    public RecEnvoi envoyer(Long clientId, Long modeleId, String canal, Long expediteurId, String login) {
        RecModele modele = modeleService.parId(modeleId)
                .orElseThrow(() -> new ValidationException("modele", "Modèle introuvable."));
        Client client = em.find(Client.class, clientId);
        if (client == null) { throw new ValidationException("client", "Client introuvable."); }

        Map<String, String> vars = variablesService.resoudre(clientId);
        String corps = variablesService.personnaliser(modele.getCorps(), vars);
        String sujet = variablesService.personnaliser(
                modele.getSujet() == null ? "" : modele.getSujet(), vars);
        String c = (canal == null || canal.trim().isEmpty()) ? modele.getCanal() : canal.trim().toUpperCase();
        if ("TOUS".equals(c)) { c = "WHATSAPP"; }

        RecEnvoi env = new RecEnvoi();
        env.setClientId(clientId);
        env.setModeleId(modeleId);
        env.setCanal(c);
        env.setSujet(sujet);
        env.setMessage(corps);
        env.setCreePar(login);

        try {
            if ("EMAIL".equals(c)) {
                String email = client.getEmailPrincipal();
                if (email == null || email.trim().isEmpty()) {
                    throw new ValidationException("email", "Aucun e-mail principal pour ce client.");
                }
                if (!mailService.estConfigure()) {
                    throw new ValidationException("smtp", "Le serveur e-mail (SMTP) n'est pas configuré.");
                }
                mailService.envoyer(Collections.singletonList(email.trim()),
                        sujet == null || sujet.isEmpty() ? "Relance" : sujet, corps);
                env.setDestinataire(email.trim());
                env.setStatut("ENVOYE");
            } else {
                String numero = numeroWhatsapp(clientId);
                if (numero == null) {
                    throw new ValidationException("numero", "Aucun numéro WhatsApp pour ce client.");
                }
                WhatsappAccount compte = compteActif();
                if (compte == null) {
                    throw new ValidationException("compte", "Aucun compte WhatsApp actif configuré.");
                }
                Message m = whatsappService.envoyerTexte(compte.getId(), numero, corps, expediteurId);
                env.setDestinataire(numero);
                env.setWaMessageId(m == null ? null : m.getWaMessageId());
                env.setStatut("ENVOYE");
            }
        } catch (Exception ex) {
            env.setStatut("ECHOUE");
            env.setErreur(ex.getMessage());
        }
        em.persist(env);
        return env;
    }

    private String numeroWhatsapp(Long clientId) {
        List<ClientContact> l = em.createQuery(
                "SELECT c FROM ClientContact c WHERE c.clientId = :id AND c.numeroWhatsapp IS NOT NULL " +
                "AND c.numeroWhatsapp <> '' ORDER BY c.contactPrincipal DESC, c.id ASC", ClientContact.class)
                .setParameter("id", clientId).setMaxResults(1).getResultList();
        return l.isEmpty() ? null : l.get(0).getNumeroWhatsapp();
    }

    private WhatsappAccount compteActif() {
        List<WhatsappAccount> l = em.createQuery(
                "SELECT w FROM WhatsappAccount w WHERE w.actif = true ORDER BY w.id", WhatsappAccount.class)
                .setMaxResults(1).getResultList();
        return l.isEmpty() ? null : l.get(0);
    }
}
