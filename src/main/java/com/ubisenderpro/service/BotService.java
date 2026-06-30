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
    @EJB private MailService mailService;
    @EJB private UserService userService;

    private static final String[] INTENT_ADRESSE = {
        "adresse", "ou etes vous", "ou etes-vous", "ou se trouve", "ou vous trouvez",
        "localisation", "situe", "situee", "comment vous trouver", "itineraire", "ou se situe"
    };
    private static final String[] INTENT_HORAIRES = {
        "horaire", "horaires", "ouvert", "ouverture", "fermeture", "ferme", "heure", "heures", "jours d ouverture"
    };

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

            // 2) Infos « pratiques » paramétrables : adresse, horaires.
            String adresse = parametre("bot.adresse", "");
            if (!adresse.isEmpty() && contientUnMotTableau(norm, INTENT_ADRESSE)) {
                repondre(conv, "📍 " + adresse, "ADRESSE");
                return;
            }
            String horaires = parametre("bot.horaires", "");
            if (!horaires.isEmpty() && contientUnMotTableau(norm, INTENT_HORAIRES)) {
                repondre(conv, "🕒 " + horaires, "HORAIRES");
                return;
            }

            // 3) Base de connaissance (FAQ).
            String reponseFaq = chercherFaq(norm);
            if (reponseFaq != null) {
                repondre(conv, reponseFaq, "FAQ");
                return;
            }

            // 4) Unités gratuites (UG) : « combien commander pour avoir des unités
            //    gratuites », ou question sur un produit en offre UG.
            if (intentionUG(norm)) {
                repondre(conv, chercherUG(norm), "UG");
                return;
            }

            // 5) Catalogue / promotions (si l'intention est détectée).
            if (contientUnMotTableau(norm, INTENT_CATALOGUE)) {
                String reponseCat = chercherCatalogue(norm);
                if (reponseCat != null) {
                    repondre(conv, reponseCat, "CATALOGUE");
                    return;
                }
            }

            // 6) Rien trouvé -> on passe la main à un humain.
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
        if (aDesUG(a)) {
            sb.append("\n").append(offreUG(a));
        }
        if (a.getStockDisponible() != null) {
            sb.append(a.getStockDisponible().signum() > 0 ? "\n✅ Disponible." : "\n⚠️ Actuellement en rupture.");
        }
        return sb.toString();
    }

    /** Vrai si l'article a une offre d'unités gratuites (commandez X, recevez Y). */
    private boolean aDesUG(Article a) {
        return a.getQuantiteUg() != null && a.getQuantiteUg() > 0
                && a.getQuantiteCommandee() != null && a.getQuantiteCommandee() > 0;
    }

    private String offreUG(Article a) {
        return "🎁 Offre : commandez " + a.getQuantiteCommandee()
                + ", recevez " + a.getQuantiteUg() + " gratuite(s) !";
    }

    private String montant(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    /** Détecte une question relative aux unités gratuites (UG / gratuit / offert / bonus). */
    private boolean intentionUG(String norm) {
        String[] mots = { "unite gratuite", "unites gratuites", "gratuit", "gratuite", "gratuites",
                "offert", "offerte", "offerts", "bonus" };
        if (contientUnMotTableau(norm, mots)) { return true; }
        for (String t : norm.split("\\s+")) { if (t.equals("ug")) { return true; } }
        return false;
    }

    /**
     * Réponse aux questions sur les unités gratuites : détail pour un produit cité,
     * sinon liste des offres UG en cours.
     */
    private String chercherUG(String norm) {
        // Produit précis cité dans le message et bénéficiant d'une offre UG ?
        for (String token : norm.split("\\s+")) {
            if (token.length() < 4) { continue; }
            List<Article> arts = em.createQuery(
                    "SELECT a FROM Article a WHERE a.actif = true AND a.quantiteUg > 0 AND a.quantiteCommandee > 0 " +
                    "AND (LOWER(a.designation) LIKE :t OR LOWER(a.pscode) = :p) ORDER BY a.designation",
                    Article.class)
                    .setParameter("t", "%" + token + "%")
                    .setParameter("p", token)
                    .setMaxResults(1).getResultList();
            if (!arts.isEmpty()) { return ligneArticle(arts.get(0)); }
        }
        // Sinon, liste générale des offres avec unités gratuites.
        List<Article> offres = em.createQuery(
                "SELECT a FROM Article a WHERE a.actif = true AND a.quantiteUg > 0 AND a.quantiteCommandee > 0 " +
                "ORDER BY a.designation", Article.class)
                .setMaxResults(8).getResultList();
        if (offres.isEmpty()) {
            return "Aucune offre d'unités gratuites n'est active pour le moment. "
                    + "Souhaitez-vous des informations sur un autre produit ?";
        }
        StringBuilder sb = new StringBuilder("🎁 Nos offres avec unités gratuites :");
        for (Article a : offres) {
            sb.append("\n• ").append(a.getDesignation())
              .append(" : commandez ").append(a.getQuantiteCommandee())
              .append(", recevez ").append(a.getQuantiteUg()).append(" gratuite(s)");
        }
        sb.append("\n\nIndiquez-moi le nom d'un produit pour plus de détails.");
        return sb.toString();
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
        notifierSuperviseurs(conv, raison);
    }

    /** Notifie les superviseurs par e-mail de l'escalade (si activé et SMTP configuré). */
    private void notifierSuperviseurs(Conversation conv, String raison) {
        try {
            if (!"true".equalsIgnoreCase(parametre("bot.email_escalade", "false"))) { return; }
            List<String> emails = userService.listerEmailsSuperviseurs();
            if (emails.isEmpty()) { return; }
            String nom = conv.getNomAffiche() != null ? conv.getNomAffiche() : conv.getNumeroWhatsapp();
            String sujet = "🙋 Escalade bot — " + nom;
            String corps = "Le bot a passé la main à un humain.\n\n"
                    + "Contact : " + nom + "\n"
                    + "Numéro : " + conv.getNumeroWhatsapp() + "\n"
                    + "Canal : " + (conv.getCanal() != null ? conv.getCanal() : "API") + "\n"
                    + "Raison : " + raison + "\n"
                    + "Dernier message : " + (conv.getDernierMessage() != null ? conv.getDernierMessage() : "") + "\n\n"
                    + "Ouvrez UbiSenderPro > Discussions pour reprendre cette conversation.";
            mailService.envoyer(emails, sujet, corps);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Notification e-mail d'escalade KO (conversation " + conv.getId() + ")", e);
        }
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
