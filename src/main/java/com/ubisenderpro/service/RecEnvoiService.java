package com.ubisenderpro.service;

import com.ubisenderpro.entity.Client;
import com.ubisenderpro.entity.ClientContact;
import com.ubisenderpro.entity.MediaFichier;
import com.ubisenderpro.entity.Message;
import com.ubisenderpro.entity.RecEnvoi;
import com.ubisenderpro.entity.RecModele;
import com.ubisenderpro.entity.WhatsappAccount;
import com.ubisenderpro.whatsapp.WhatsappCloudClient;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
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
    @EJB
    private MediaFichierService mediaFichierService;
    @EJB
    private RecRelevePdfService relevePdfService;
    @EJB
    private ParametreService parametreService;

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
        return envoyer(clientId, modeleId, canal, expediteurId, login, null, false);
    }

    /**
     * Envoie une relance avec pièce(s) jointe(s) optionnelle(s) et trace l'historique.
     *
     * @param pieceMediaId identifiant d'un fichier média téléversé à joindre (ou {@code null})
     * @param releveAuto   joindre un relevé de compte PDF généré automatiquement
     */
    public RecEnvoi envoyer(Long clientId, Long modeleId, String canal, Long expediteurId, String login,
                            Long pieceMediaId, boolean releveAuto) {
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

        // Résolution des pièces jointes (fichier téléversé + relevé auto).
        List<PieceResolue> pieces = resoudrePieces(clientId, pieceMediaId, releveAuto);

        RecEnvoi env = new RecEnvoi();
        env.setClientId(clientId);
        env.setModeleId(modeleId);
        env.setCanal(c);
        env.setSujet(sujet);
        env.setMessage(corps);
        env.setCreePar(login);
        env.setPieceJointe(libellePieces(pieces));

        try {
            if ("EMAIL".equals(c)) {
                String email = client.getEmailPrincipal();
                if (email == null || email.trim().isEmpty()) {
                    throw new ValidationException("email", "Aucun e-mail principal pour ce client.");
                }
                if (!mailService.estConfigure()) {
                    throw new ValidationException("smtp", "Le serveur e-mail (SMTP) n'est pas configuré.");
                }
                String objet = sujet == null || sujet.isEmpty() ? "Relance" : sujet;
                if (pieces.isEmpty()) {
                    mailService.envoyer(Collections.singletonList(email.trim()), objet, corps);
                } else {
                    List<MailService.PieceJointe> jointes = new ArrayList<>();
                    for (PieceResolue p : pieces) {
                        jointes.add(new MailService.PieceJointe(p.contenu, p.nomFichier, p.mimeType));
                    }
                    mailService.envoyerAvecPieces(Collections.singletonList(email.trim()), objet, corps, jointes);
                }
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
                env.setDestinataire(numero);
                if (modele.getNomModeleWhatsapp() != null && !modele.getNomModeleWhatsapp().trim().isEmpty()) {
                    // Template Meta approuvé (valable hors fenêtre 24h) : paramètres résolus depuis les variables finance.
                    WhatsappCloudClient cloud = new WhatsappCloudClient(compte);
                    WhatsappCloudClient.SendResult res = cloud.envoyerModele(
                            numero, modele.getNomModeleWhatsapp().trim(), "fr",
                            parametres(modele.getParamsCorps(), vars), null, null);
                    if (res != null && res.success) {
                        env.setWaMessageId(res.waMessageId);
                        env.setStatut("ENVOYE");
                    } else {
                        env.setStatut("ECHOUE");
                        env.setErreur(res == null ? "Échec de l'envoi du template." : res.erreur);
                    }
                } else {
                    // Texte libre (valable uniquement dans la fenêtre de 24h).
                    Message m = whatsappService.envoyerTexte(compte.getId(), numero, corps, expediteurId);
                    env.setWaMessageId(m == null ? null : m.getWaMessageId());
                    env.setStatut("ENVOYE");
                }
                // Pièces jointes WhatsApp : envoyées en messages média séparés (best-effort, fenêtre 24h).
                if ("ENVOYE".equals(env.getStatut()) && !pieces.isEmpty()) {
                    envoyerPiecesWhatsapp(compte.getId(), numero, pieces, expediteurId, env);
                }
            }
        } catch (Exception ex) {
            env.setStatut("ECHOUE");
            env.setErreur(ex.getMessage());
        }
        em.persist(env);
        return env;
    }

    /** Charge le fichier téléversé et/ou génère le relevé PDF, et persiste ce dernier comme média. */
    private List<PieceResolue> resoudrePieces(Long clientId, Long pieceMediaId, boolean releveAuto) {
        List<PieceResolue> pieces = new ArrayList<>();
        if (pieceMediaId != null) {
            MediaFichier mf = mediaFichierService.parId(pieceMediaId).orElse(null);
            if (mf != null && mf.getContenu() != null) {
                pieces.add(new PieceResolue(mf.getId(), mf.getContenu(),
                        mf.getNomFichier() == null || mf.getNomFichier().isEmpty() ? "piece-jointe" : mf.getNomFichier(),
                        mf.getMimeType() == null || mf.getMimeType().isEmpty() ? "application/octet-stream" : mf.getMimeType()));
            }
        }
        if (releveAuto) {
            byte[] pdf = relevePdfService.genererReleve(clientId);
            String nom = relevePdfService.nomFichier(clientId);
            // Persisté comme média afin de disposer d'une URL publique pour WhatsApp.
            MediaFichier mf = mediaFichierService.enregistrer(pdf, "application/pdf", nom);
            pieces.add(new PieceResolue(mf.getId(), pdf, nom, "application/pdf"));
        }
        return pieces;
    }

    /** Envoie chaque pièce comme média WhatsApp via son URL publique (document ou image). */
    private void envoyerPiecesWhatsapp(Long accountId, String numero, List<PieceResolue> pieces,
                                       Long expediteurId, RecEnvoi env) {
        for (PieceResolue p : pieces) {
            try {
                String url = urlPublique(p.mediaId);
                String type = p.mimeType != null && p.mimeType.toLowerCase().startsWith("image/")
                        ? "image" : "document";
                whatsappService.envoyerMedia(accountId, numero, type, url, p.nomFichier, expediteurId);
            } catch (Exception ex) {
                // La relance texte est partie ; on signale seulement l'échec d'envoi de la pièce.
                env.setErreur("Relance envoyée, mais échec d'envoi de la pièce jointe : " + ex.getMessage());
            }
        }
    }

    /** URL publique d'un média (param app.url_base derrière reverse proxy HTTPS). */
    private String urlPublique(Long mediaId) {
        String base = parametreService.valeur("app.url_base", "");
        if (base == null || base.trim().isEmpty()) {
            throw new IllegalStateException("Paramètre 'app.url_base' requis pour joindre un média WhatsApp.");
        }
        String b = base.trim();
        return b + (b.endsWith("/") ? "" : "/") + "media/" + mediaId;
    }

    private String libellePieces(List<PieceResolue> pieces) {
        if (pieces.isEmpty()) { return null; }
        StringBuilder sb = new StringBuilder();
        for (PieceResolue p : pieces) {
            if (sb.length() > 0) { sb.append(", "); }
            sb.append(p.nomFichier);
        }
        String s = sb.toString();
        return s.length() > 255 ? s.substring(0, 255) : s;
    }

    /** Pièce jointe résolue : binaire + métadonnées + identifiant média (pour l'URL WhatsApp). */
    private static final class PieceResolue {
        final Long mediaId;
        final byte[] contenu;
        final String nomFichier;
        final String mimeType;

        PieceResolue(Long mediaId, byte[] contenu, String nomFichier, String mimeType) {
            this.mediaId = mediaId;
            this.contenu = contenu;
            this.nomFichier = nomFichier;
            this.mimeType = mimeType;
        }
    }

    /** Valeurs ordonnées des paramètres {{1}},{{2}}… d'un template, depuis les variables finance. */
    private List<String> parametres(String spec, Map<String, String> vars) {
        List<String> out = new ArrayList<>();
        if (spec == null || spec.trim().isEmpty()) {
            out.add(vars.getOrDefault("nom_client", ""));
            return out;
        }
        for (String token : spec.split(",")) {
            String k = token.trim().toLowerCase();
            if (k.isEmpty()) { continue; }
            out.add(vars.getOrDefault(k, ""));
        }
        return out;
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
