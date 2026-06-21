package com.ubisenderpro.service;

import com.ubisenderpro.entity.SegmentationClient;
import com.ubisenderpro.entity.WaBulkDestinataire;
import com.ubisenderpro.entity.WaBulkJob;
import com.ubisenderpro.whatsapp.WaWebClient;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Étapes transactionnelles unitaires de l'envoi en masse WhatsApp Web.
 * Un envoi = une transaction (REQUIRES_NEW) avec rotation des variantes.
 */
@Stateless
public class WaBulkSenderTx {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private ParametreService parametreService;

    private final WaWebClient client = new WaWebClient();

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void marquerStatut(Long jobId, String statut) {
        WaBulkJob j = em.find(WaBulkJob.class, jobId);
        if (j != null) { j.setStatut(statut); em.merge(j); }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public String statutJob(Long jobId) {
        WaBulkJob j = em.find(WaBulkJob.class, jobId);
        return j == null ? null : j.getStatut();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public List<Long> idsEnAttente(Long jobId) {
        return em.createQuery(
                "SELECT d.id FROM WaBulkDestinataire d WHERE d.jobId = :j AND d.statut = 'EN_ATTENTE'",
                Long.class).setParameter("j", jobId).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void envoyer(Long destinataireId) {
        WaBulkDestinataire d = em.find(WaBulkDestinataire.class, destinataireId);
        if (d == null || !"EN_ATTENTE".equals(d.getStatut())) return;
        WaBulkJob job = em.find(WaBulkJob.class, d.getJobId());
        if (job == null) return;

        String sessionId = WaWebSessionService.nodeId(job.getSessionId());
        String texte = personnaliser(choisirVariante(job), d.getNumero(), d.getNom());

        WaWebClient.SendResult res = envoyerContenu(job, sessionId, d.getNumero(), texte);

        if (res.success) {
            d.setStatut("ENVOYE");
            d.setSentAt(LocalDateTime.now());
            job.setEnvoyes(job.getEnvoyes() + 1);
        } else {
            d.setStatut("ECHEC");
            d.setErreur(res.erreur);
            job.setEchoues(job.getEchoues() + 1);
        }
        em.merge(d);
        em.merge(job);
    }

    /**
     * Envoie le contenu d'un message : pièces jointes multiples (medias_json) si présentes,
     * sinon média unique, sinon texte. Le texte sert de légende à la 1re pièce jointe.
     */
    private WaWebClient.SendResult envoyerContenu(WaBulkJob job, String sessionId, String numero, String texte) {
        List<Map<String, String>> medias = mediasDuJob(job);
        if (!medias.isEmpty()) {
            WaWebClient.SendResult dernier = null;
            for (int i = 0; i < medias.size(); i++) {
                Map<String, String> m = medias.get(i);
                String type = estVide(m.get("type")) ? "image" : m.get("type");
                String legende = (i == 0) ? texte : null; // légende sur la 1re pièce jointe
                dernier = client.sendMedia(sessionId, numero, type, m.get("url"), legende, m.get("mime"), m.get("nom"));
                if (!dernier.success) { return dernier; } // échec dès qu'une pièce jointe échoue
            }
            return dernier;
        }
        if (job.getMediaUrl() != null && !job.getMediaUrl().isEmpty()) {
            return client.sendMedia(sessionId, numero,
                    job.getMediaType() == null ? "image" : job.getMediaType(),
                    job.getMediaUrl(), texte, job.getMediaMime(), job.getMediaNom());
        }
        return client.sendText(sessionId, numero, texte);
    }

    /** Désérialise medias_json en liste [{url,type,mime,nom}] (vide si absent/illisible). */
    @SuppressWarnings("unchecked")
    private List<Map<String, String>> mediasDuJob(WaBulkJob job) {
        if (job.getMediasJson() == null || job.getMediasJson().trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        try {
            List<Map<String, String>> l = MAPPER.readValue(job.getMediasJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {});
            List<Map<String, String>> out = new ArrayList<>();
            for (Map<String, String> m : l) {
                if (m != null && m.get("url") != null && !m.get("url").isEmpty()) { out.add(m); }
            }
            return out;
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private boolean estVide(String s) { return s == null || s.trim().isEmpty(); }

    /** Choisit aléatoirement une variante non vide parmi les 5 (anti-spam). */
    private String choisirVariante(WaBulkJob j) {
        List<String> variantes = new ArrayList<>();
        for (String v : new String[]{j.getMsg1(), j.getMsg2(), j.getMsg3(), j.getMsg4(), j.getMsg5()}) {
            if (v != null && !v.trim().isEmpty()) { variantes.add(v); }
        }
        if (variantes.isEmpty()) return "";
        return variantes.get(ThreadLocalRandom.current().nextInt(variantes.size()));
    }

    /**
     * Remplace les variables du message par les valeurs du destinataire.
     * Tokens [VARIABLE] (style WASender) + compat {{variable}}.
     */
    private String personnaliser(String texte, String numero, String nomDest) {
        if (texte == null) return "";
        Map<String, String> vars = resoudreVariables(numero, nomDest);
        String out = texte;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String val = e.getValue() == null ? "" : e.getValue();
            out = out.replace("[" + e.getKey() + "]", val)
                     .replace("{{" + e.getKey().toLowerCase() + "}}", val);
        }
        return out;
    }

    /** Construit la table des variables disponibles pour un destinataire. */
    private Map<String, String> resoudreVariables(String numero, String nomDest) {
        String nom = nomDest == null ? "" : nomDest;
        String tel = "", email = "", societeClient = "", segmentation = "", ville = "", region = "";

        // Résolution du contact/client par numéro (si présent en base).
        try {
            List<Object[]> rows = em.createQuery(
                    "SELECT ct.nomComplet, ct.telephonePrincipal, ct.email, cl.nomCompte, " +
                    "cl.segmentationId, cl.ville, cl.region FROM ClientContact ct, Client cl " +
                    "WHERE ct.clientId = cl.id AND ct.numeroWhatsapp = :n", Object[].class)
                    .setParameter("n", numero).setMaxResults(1).getResultList();
            if (!rows.isEmpty()) {
                Object[] r = rows.get(0);
                if ((nom == null || nom.isEmpty()) && r[0] != null) { nom = (String) r[0]; }
                tel = vide(r[1]); email = vide(r[2]); societeClient = vide(r[3]);
                ville = vide(r[5]); region = vide(r[6]);
                if (r[4] != null) {
                    SegmentationClient s = em.find(SegmentationClient.class, (Long) r[4]);
                    if (s != null) { segmentation = s.getLibelle(); }
                }
            }
        } catch (Exception ignore) { /* destinataire hors base : variables client vides */ }

        String societeEmettrice = parametreService.valeur("app.societe",
                parametreService.valeur("app.nom", ""));

        Map<String, String> vars = new HashMap<>();
        vars.put("TEL_SOCIETE", parametreService.valeur("app.societe_tel", ""));
        vars.put("SITE", parametreService.valeur("app.site", ""));
        vars.put("LIEN_COMMANDE", parametreService.valeur("app.lien_commande", ""));
        vars.put("NOM", nom);
        vars.put("NAME", nom);            // compat
        vars.put("NOM_CONTACT", nom);     // compat {{nom_contact}}
        vars.put("TELEPHONE", tel);
        vars.put("EMAIL", email);
        vars.put("SOCIETE_CLIENT", societeClient);
        vars.put("SEGMENTATION", segmentation);
        vars.put("VILLE", ville);
        vars.put("REGION", region);
        vars.put("SOCIETE", societeEmettrice);
        return vars;
    }

    private String vide(Object o) { return o == null ? "" : String.valueOf(o); }
}
