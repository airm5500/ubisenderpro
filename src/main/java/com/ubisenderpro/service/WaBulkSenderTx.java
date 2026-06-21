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

        WaWebClient.SendResult res;
        if (job.getMediaUrl() != null && !job.getMediaUrl().isEmpty()) {
            res = client.sendMedia(sessionId, d.getNumero(),
                    job.getMediaType() == null ? "image" : job.getMediaType(),
                    job.getMediaUrl(), texte, job.getMediaMime(), job.getMediaNom());
        } else {
            res = client.sendText(sessionId, d.getNumero(), texte);
        }

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
