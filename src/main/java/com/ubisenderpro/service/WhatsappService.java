package com.ubisenderpro.service;

import com.ubisenderpro.dto.MediaUploadRequest;
import com.ubisenderpro.entity.Conversation;
import com.ubisenderpro.entity.Message;
import com.ubisenderpro.entity.MessageMedia;
import com.ubisenderpro.entity.WhatsappAccount;
import com.ubisenderpro.whatsapp.WhatsappCloudClient;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Gestion des comptes WhatsApp et envoi de messages via la Cloud API.
 */
@Stateless
public class WhatsappService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<WhatsappAccount> listerComptes() {
        return em.createQuery("SELECT w FROM WhatsappAccount w ORDER BY w.libelle", WhatsappAccount.class).getResultList();
    }

    public Optional<WhatsappAccount> compte(Long id) { return Optional.ofNullable(em.find(WhatsappAccount.class, id)); }

    /** Modèles (templates) approuvés/connus côté Meta pour un compte. */
    public java.util.List<java.util.Map<String, Object>> templatesMeta(Long accountId) {
        WhatsappAccount a = em.find(WhatsappAccount.class, accountId);
        if (a == null) { throw new IllegalArgumentException("Compte WhatsApp introuvable"); }
        return new com.ubisenderpro.whatsapp.WhatsappCloudClient(a).listerTemplates();
    }

    public WhatsappAccount creerCompte(WhatsappAccount a) { em.persist(a); return a; }
    public WhatsappAccount modifierCompte(WhatsappAccount a) {
        WhatsappAccount ex = em.find(WhatsappAccount.class, a.getId());
        if (ex != null) { a.setCreatedAt(ex.getCreatedAt()); }
        a.setUpdatedAt(LocalDateTime.now());
        return em.merge(a);
    }

    public void supprimerCompte(Long id) {
        WhatsappAccount ex = em.find(WhatsappAccount.class, id);
        if (ex != null) { em.remove(ex); }
    }

    public Optional<WhatsappAccount> compteParPhoneNumberId(String phoneNumberId) {
        List<WhatsappAccount> l = em.createQuery(
                "SELECT w FROM WhatsappAccount w WHERE w.phoneNumberId = :p", WhatsappAccount.class)
                .setParameter("p", phoneNumberId).setMaxResults(1).getResultList();
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    /**
     * Retrouve ou crée la conversation pour un numéro donné sur un compte donné.
     */
    public Conversation conversationPour(Long accountId, String numero, String nomAffiche) {
        List<Conversation> l = em.createQuery(
                "SELECT c FROM Conversation c WHERE c.whatsappAccountId = :a AND c.numeroWhatsapp = :n",
                Conversation.class)
                .setParameter("a", accountId).setParameter("n", numero)
                .setMaxResults(1).getResultList();
        if (!l.isEmpty()) return l.get(0);
        Conversation c = new Conversation();
        c.setWhatsappAccountId(accountId);
        c.setNumeroWhatsapp(numero);
        c.setNomAffiche(nomAffiche);
        c.setStatut("OUVERTE");
        em.persist(c);
        em.flush(); // génère l'identifiant (IDENTITY) avant utilisation comme clé étrangère
        return c;
    }

    /**
     * Envoie un message texte sortant dans une conversation et enregistre le suivi.
     */
    public Message envoyerTexte(Long accountId, String numero, String texte, Long expediteurId) {
        WhatsappAccount account = em.find(WhatsappAccount.class, accountId);
        if (account == null) throw new IllegalArgumentException("Compte WhatsApp introuvable");

        Conversation conv = conversationPour(accountId, numero, null);

        Message msg = new Message();
        msg.setConversationId(conv.getId());
        msg.setDirection("SORTANT");
        msg.setTypeMessage("TEXTE");
        msg.setContenu(texte);
        msg.setExpediteurId(expediteurId);

        WhatsappCloudClient client = new WhatsappCloudClient(account);
        WhatsappCloudClient.SendResult res = client.envoyerTexte(numero, texte);
        if (res.success) {
            msg.setStatut("ENVOYE");
            msg.setWaMessageId(res.waMessageId);
        } else {
            msg.setStatut("ECHOUE");
            msg.setErreur(res.erreur);
        }
        em.persist(msg);

        conv.setDernierMessage(texte);
        conv.setDateDernierMessage(LocalDateTime.now());
        em.merge(conv);
        return msg;
    }

    public Message envoyerMedia(Long accountId, String numero, String type, String url, String legende, Long expediteurId) {
        WhatsappAccount account = em.find(WhatsappAccount.class, accountId);
        if (account == null) throw new IllegalArgumentException("Compte WhatsApp introuvable");
        Conversation conv = conversationPour(accountId, numero, null);

        Message msg = creerMessageMedia(conv.getId(), type, legende != null ? legende : url, expediteurId);

        WhatsappCloudClient client = new WhatsappCloudClient(account);
        WhatsappCloudClient.SendResult res = client.envoyerMedia(numero, type, url, legende);
        appliquerResultat(msg, res);
        em.persist(msg);
        enregistrerMedia(msg, type, null, url, null, null);

        conv.setDateDernierMessage(LocalDateTime.now());
        em.merge(conv);
        return msg;
    }

    /**
     * Envoie un média préalablement téléversé sur Meta, identifié par son media_id.
     * Évite l'hébergement public exigé par l'envoi via URL.
     */
    public Message envoyerMediaParId(Long accountId, String numero, String type, String mediaId,
                                     String legende, String mimeType, String nomFichier, Long expediteurId) {
        WhatsappAccount account = em.find(WhatsappAccount.class, accountId);
        if (account == null) throw new IllegalArgumentException("Compte WhatsApp introuvable");
        if (mediaId == null || mediaId.isEmpty()) throw new IllegalArgumentException("media_id manquant");
        Conversation conv = conversationPour(accountId, numero, null);

        Message msg = creerMessageMedia(conv.getId(), type,
                legende != null ? legende : (nomFichier != null ? nomFichier : type), expediteurId);

        WhatsappCloudClient client = new WhatsappCloudClient(account);
        WhatsappCloudClient.SendResult res = client.envoyerMediaParId(numero, type, mediaId, legende);
        appliquerResultat(msg, res);
        em.persist(msg);
        enregistrerMedia(msg, type, mediaId, null, mimeType, nomFichier);

        conv.setDateDernierMessage(LocalDateTime.now());
        em.merge(conv);
        return msg;
    }

    /**
     * Téléverse un fichier binaire vers l'API WhatsApp et renvoie le media_id obtenu.
     * Le fichier est transmis en base64 (convention partagée avec l'assistant d'import).
     */
    public String uploadMedia(MediaUploadRequest req) {
        if (req == null || req.getFichierBase64() == null || req.getFichierBase64().isEmpty()) {
            throw new IllegalArgumentException("Fichier manquant");
        }
        WhatsappAccount account = em.find(WhatsappAccount.class, req.getAccountId());
        if (account == null) throw new IllegalArgumentException("Compte WhatsApp introuvable");

        byte[] contenu = Base64.getDecoder().decode(req.getFichierBase64());
        WhatsappCloudClient client = new WhatsappCloudClient(account);
        WhatsappCloudClient.UploadResult res = client.uploadMedia(contenu, req.getMimeType(), req.getNomFichier());
        if (!res.success) {
            throw new IllegalStateException("Téléversement WhatsApp en échec : " + res.erreur);
        }
        return res.mediaId;
    }

    private Message creerMessageMedia(Long conversationId, String type, String contenu, Long expediteurId) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setDirection("SORTANT");
        msg.setTypeMessage(type.toUpperCase());
        msg.setContenu(contenu);
        msg.setExpediteurId(expediteurId);
        return msg;
    }

    private void appliquerResultat(Message msg, WhatsappCloudClient.SendResult res) {
        if (res.success) { msg.setStatut("ENVOYE"); msg.setWaMessageId(res.waMessageId); }
        else { msg.setStatut("ECHOUE"); msg.setErreur(res.erreur); }
    }

    private void enregistrerMedia(Message msg, String type, String waMediaId, String url,
                                  String mimeType, String nomFichier) {
        em.flush(); // garantit l'identifiant généré du message
        MessageMedia media = new MessageMedia();
        media.setMessageId(msg.getId());
        media.setTypeMedia(type.toUpperCase());
        media.setWaMediaId(waMediaId);
        media.setUrl(url);
        media.setMimeType(mimeType);
        media.setNomFichier(nomFichier);
        em.persist(media);
    }
}
