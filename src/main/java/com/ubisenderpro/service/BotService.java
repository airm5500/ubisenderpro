package com.ubisenderpro.service;

import com.ubisenderpro.entity.Article;
import com.ubisenderpro.entity.BotFaq;
import com.ubisenderpro.entity.Conversation;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Assistant automatique « à règles » (#5 du backlog) — désactivé par défaut.
 * Répond aux messages entrants à partir d'une base FAQ et du catalogue/promotions,
 * et passe la main à un humain quand il ne sait pas répondre ou sur mots-clés sensibles.
 *
 * Déclenché depuis les points d'entrée (WebhookService pour la Cloud API,
 * WaWebEventResource pour WhatsApp Web) afin d'éviter tout cycle d'injection EJB.
 */
@Stateless
public class BotService {

    private static final Logger LOG = Logger.getLogger(BotService.class.getName());

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB private ParametreService parametreService;
    @EJB private BotFaqService faqService;
    @EJB private WhatsappService whatsappService;
    @EJB private WaWebSessionService waWebSessionService;
    @EJB private JournalService journalService;

    private static final String[] INTENT_CATALOGUE = {
        "prix", "combien", "coute", "cout", "tarif", "promo", "promotion",
        "reduction", "dispo", "disponible", "stock", "avez vous", "avez-vous"
    };

    /** Traite un message entrant et, si le bot est actif, y répond ou escalade. */
    public void traiterEntrant(Long conversationId, String texte) {
        try {
            if (!estActif()) { return; }
            if (texte == null || texte.trim().isEmpty()) { return; }
            Conversation conv = em.find(Conversation.class, conversationId);
            if (conv == null) { return; }
            // Le bot se tait si un humain a pris la conversation ou a repris la main.
            if (conv.getAgentId() != null || !conv.isBotActif()) { return; }

            String norm = normaliser(texte);

            // 1) Demande explicite d'un humain ou sujet sensible -> escalade.
            if (contientUnMot(norm, parametre("bot.mots_cles_humain", ""))
                    || contientUnMot(norm, parametre("bot.mots_cles_escalade", ""))) {
                escalader(conv, "mot-clé sensible / demande d'un humain");
                return;
            }

            // 2) Base de connaissance (FAQ).
            String reponseFaq = chercherFaq(norm);
            if (reponseFaq != null) {
                repondre(conv, reponseFaq, "FAQ");
                return;
            }

            // 3) Catalogue / promotions (si l'intention est détectée).
            if (contientUnMotTableau(norm, INTENT_CATALOGUE)) {
                String reponseCat = chercherCatalogue(norm);
                if (reponseCat != null) {
                    repondre(conv, reponseCat, "CATALOGUE");
                    return;
                }
            }

            // 4) Rien trouvé -> on passe la main à un humain.
            escalader(conv, "aucune règle ne correspond");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Bot KO sur la conversation " + conversationId, e);
        }
    }

    private boolean estActif() {
        return "true".equalsIgnoreCase(parametre("bot.actif", "false"));
    }

    private String parametre(String cle, String defaut) {
        try { return parametreService.valeur(cle, defaut); } catch (Exception e) { return defaut; }
    }

    /** Cherche une entrée FAQ dont un déclencheur est contenu dans le message. */
    private String chercherFaq(String norm) {
        List<BotFaq> faqs = faqService.listerActives();
        for (BotFaq f : faqs) {
            if (contientUnMot(norm, f.getDeclencheurs())) { return f.getReponse(); }
        }
        return null;
    }

    /** Cherche un article du catalogue référencé dans le message et compose une réponse. */
    private String chercherCatalogue(String norm) {
        for (String token : norm.split("\\s+")) {
            if (token.length() < 4) { continue; }
            List<Article> arts = em.createQuery(
                    "SELECT a FROM Article a WHERE a.actif = true AND " +
                    "(LOWER(a.designation) LIKE :t OR LOWER(a.pscode) = :p) ORDER BY a.designation",
                    Article.class)
                    .setParameter("t", "%" + token + "%")
                    .setParameter("p", token)
                    .setMaxResults(1).getResultList();
            if (!arts.isEmpty()) { return ligneArticle(arts.get(0)); }
        }
        return null;
    }

    private String ligneArticle(Article a) {
        StringBuilder sb = new StringBuilder("📦 ").append(a.getDesignation());
        if (a.getPrixVente() != null) {
            sb.append(" : ").append(montant(a.getPrixVente())).append(" FCFA");
        }
        if (a.getPrixPromotionnel() != null && a.getPrixPromotionnel().signum() > 0) {
            sb.append(" — en promotion à ").append(montant(a.getPrixPromotionnel())).append(" FCFA 🎉");
        }
        if (a.getStockDisponible() != null) {
            sb.append(a.getStockDisponible().signum() > 0 ? "\n✅ Disponible." : "\n⚠️ Actuellement en rupture.");
        }
        return sb.toString();
    }

    private String montant(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    /** Envoie une réponse automatique sur le canal de la conversation et la journalise. */
    private void repondre(Conversation conv, String message, String origine) {
        envoyer(conv, message);
        journalService.tracer(null, "BOT", "BOT_REPONSE", "Conversation", conv.getId(),
                "Réponse automatique (" + origine + ") : " + apercu(message), null);
    }

    /** Passe la main à un humain : coupe le bot, marque « à reprendre », prévient le client. */
    private void escalader(Conversation conv, String raison) {
        conv.setBotActif(false);
        conv.setStatut("A_REPRENDRE");
        em.merge(conv);
        String transfert = parametre("bot.message_transfert",
                "Un instant, je vous mets en relation avec un conseiller.");
        envoyer(conv, transfert);
        journalService.tracer(null, "BOT", "BOT_ESCALADE", "Conversation", conv.getId(),
                "Passage à un humain (" + raison + ")", null);
    }

    private void envoyer(Conversation conv, String message) {
        try {
            if ("WEB".equals(conv.getCanal())) {
                waWebSessionService.envoyerTexte(conv.getWaWebSessionId(), conv.getNumeroWhatsapp(), message, null);
            } else {
                whatsappService.envoyerTexte(conv.getWhatsappAccountId(), conv.getNumeroWhatsapp(), message, null);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Bot : envoi impossible sur la conversation " + conv.getId(), e);
        }
    }

    /* ---------- Utilitaires de correspondance texte ---------- */

    /** Normalise : minuscules, sans accents, espaces simples. */
    private String normaliser(String s) {
        String n = Normalizer.normalize(s.toLowerCase().trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.replaceAll("\\s+", " ");
    }

    /** Vrai si l'un des mots-clés (séparés par des virgules) est contenu dans le texte normalisé. */
    private boolean contientUnMot(String norm, String motsClesCsv) {
        if (motsClesCsv == null) { return false; }
        for (String mot : motsClesCsv.split(",")) {
            String m = normaliser(mot);
            if (!m.isEmpty() && norm.contains(m)) { return true; }
        }
        return false;
    }

    private boolean contientUnMotTableau(String norm, String[] mots) {
        for (String m : mots) { if (norm.contains(m)) { return true; } }
        return false;
    }

    private String apercu(String s) {
        return s.length() > 120 ? s.substring(0, 117) + "..." : s;
    }
}
