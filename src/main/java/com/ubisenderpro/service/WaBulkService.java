package com.ubisenderpro.service;

import com.ubisenderpro.dto.WaBulkRequest;
import com.ubisenderpro.entity.WaBulkDestinataire;
import com.ubisenderpro.entity.WaBulkJob;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

/**
 * Orchestration de l'envoi en masse WhatsApp Web : création du travail,
 * parsing des destinataires, lancement asynchrone, suivi.
 */
@Stateless
public class WaBulkService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private WaBulkSenderAsync sender;

    public WaBulkJob creer(WaBulkRequest req) {
        if (req.getSessionId() == null) throw new IllegalArgumentException("Session requise");

        WaBulkJob j = new WaBulkJob();
        j.setSessionId(req.getSessionId());
        j.setNom(req.getNom());
        j.setMsg1(req.getMsg1()); j.setMsg2(req.getMsg2()); j.setMsg3(req.getMsg3());
        j.setMsg4(req.getMsg4()); j.setMsg5(req.getMsg5());
        j.setMediaUrl(vide(req.getMediaUrl()) ? null : req.getMediaUrl());
        j.setMediaType(req.getMediaType());
        j.setMediaMime(req.getMediaMime());
        j.setMediaNom(req.getMediaNom());
        if (req.getAttenteMin() != null) j.setAttenteMin(req.getAttenteMin());
        if (req.getAttenteMax() != null) j.setAttenteMax(req.getAttenteMax());
        if (req.getPauseApres() != null) j.setPauseApres(req.getPauseApres());
        if (req.getPauseMin() != null) j.setPauseMin(req.getPauseMin());
        if (req.getPauseMax() != null) j.setPauseMax(req.getPauseMax());

        if (vide(j.getMsg1()) && vide(j.getMsg2()) && vide(j.getMsg3())
                && vide(j.getMsg4()) && vide(j.getMsg5()) && vide(j.getMediaUrl())) {
            throw new IllegalArgumentException("Renseignez au moins une variante de message ou une pièce jointe");
        }

        em.persist(j);
        em.flush();

        int total = 0;
        for (String ligne : decouper(req.getDestinatairesTexte())) {
            String[] parts = ligne.split("[;,\\t]", 2);
            String numero = parts[0].replaceAll("[^0-9]", "");
            if (numero.length() < 6) { continue; }
            String nom = parts.length > 1 ? parts[1].trim() : null;
            WaBulkDestinataire d = new WaBulkDestinataire();
            d.setJobId(j.getId());
            d.setNumero(numero);
            d.setNom(nom);
            em.persist(d);
            total++;
        }
        j.setTotal(total);
        return em.merge(j);
    }

    public WaBulkJob lancer(Long jobId) {
        WaBulkJob j = em.find(WaBulkJob.class, jobId);
        if (j == null) throw new IllegalArgumentException("Travail introuvable");
        if (j.getTotal() == 0) throw new IllegalArgumentException("Aucun destinataire");
        sender.lancer(jobId);
        return j;
    }

    public List<WaBulkJob> lister() {
        return em.createQuery("SELECT j FROM WaBulkJob j ORDER BY j.id DESC", WaBulkJob.class)
                .setMaxResults(100).getResultList();
    }

    public Optional<WaBulkJob> parId(Long id) { return Optional.ofNullable(em.find(WaBulkJob.class, id)); }

    private boolean vide(String s) { return s == null || s.trim().isEmpty(); }

    private String[] decouper(String texte) {
        if (texte == null || texte.trim().isEmpty()) { return new String[0]; }
        return texte.split("\\r?\\n");
    }
}
