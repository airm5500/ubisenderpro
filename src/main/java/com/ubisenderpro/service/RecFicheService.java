package com.ubisenderpro.service;

import com.ubisenderpro.entity.Client;
import com.ubisenderpro.entity.RecFiche;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fiches recouvrement : complément financier par client, avec calcul du solde
 * (Solde = encours initial + factures − avoirs − règlements).
 */
@Stateless
public class RecFicheService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public Optional<RecFiche> parId(Long id) { return Optional.ofNullable(em.find(RecFiche.class, id)); }

    public Optional<RecFiche> parClient(Long clientId) {
        List<RecFiche> l = em.createQuery("SELECT f FROM RecFiche f WHERE f.clientId = :c", RecFiche.class)
                .setParameter("c", clientId).setMaxResults(1).getResultList();
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    public RecFiche creer(RecFiche f) {
        valider(f, true);
        em.persist(f);
        return f;
    }

    public RecFiche modifier(RecFiche f) {
        valider(f, false);
        RecFiche ex = em.find(RecFiche.class, f.getId());
        if (ex == null) { throw new ValidationException("id", "Fiche introuvable."); }
        ex.setSegmentCommercial(f.getSegmentCommercial());
        ex.setProfilPaiement(f.getProfilPaiement());
        ex.setResponsable(f.getResponsable());
        ex.setStatut(f.getStatut());
        ex.setCanalPrefere(f.getCanalPrefere());
        ex.setObservations(f.getObservations());
        ex.setEncoursInitial(f.getEncoursInitial() == null ? BigDecimal.ZERO : f.getEncoursInitial());
        ex.setDateSituation(f.getDateSituation());
        ex.setUpdatedAt(LocalDateTime.now());
        return em.merge(ex);
    }

    private void valider(RecFiche f, boolean creation) {
        if (f.getClientId() == null) {
            throw new ValidationException("clientId", "Le client est obligatoire.");
        }
        if (creation && parClient(f.getClientId()).isPresent()) {
            throw new ValidationException("clientId", "Une fiche recouvrement existe déjà pour ce client.");
        }
    }

    public void supprimer(Long id) { parId(id).ifPresent(em::remove); }

    /** Solde courant d'un client : encours initial + factures − avoirs − règlements. */
    public BigDecimal solde(Long clientId) {
        Map<String, Object> s = situation(clientId);
        return (BigDecimal) s.get("solde");
    }

    /** Situation détaillée d'un client (encours, totaux, solde). */
    public Map<String, Object> situation(Long clientId) {
        BigDecimal encours = parClient(clientId).map(RecFiche::getEncoursInitial).orElse(BigDecimal.ZERO);
        BigDecimal factures = somme("SELECT COALESCE(SUM(c.montant),0) FROM RecCreance c WHERE c.clientId = :id AND c.type = 'FACTURE'", clientId);
        BigDecimal avoirs = somme("SELECT COALESCE(SUM(c.montant),0) FROM RecCreance c WHERE c.clientId = :id AND c.type = 'AVOIR'", clientId);
        BigDecimal paiements = somme("SELECT COALESCE(SUM(p.montant),0) FROM RecPaiement p WHERE p.clientId = :id", clientId);
        if (encours == null) { encours = BigDecimal.ZERO; }
        BigDecimal solde = encours.add(factures).subtract(avoirs).subtract(paiements);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("encoursInitial", encours);
        m.put("totalFactures", factures);
        m.put("totalAvoirs", avoirs);
        m.put("totalPaiements", paiements);
        m.put("solde", solde);
        return m;
    }

    private BigDecimal somme(String jpql, Long clientId) {
        BigDecimal v = em.createQuery(jpql, BigDecimal.class).setParameter("id", clientId).getSingleResult();
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * Liste des fiches avec nom du client, agence et solde — pour la grille.
     * Filtres optionnels (recherche nom/numéro, agence, segment, profil).
     */
    public List<Map<String, Object>> listerAvecSolde(String recherche, String agence,
                                                     String segment, String profil) {
        List<RecFiche> fiches = em.createQuery("SELECT f FROM RecFiche f ORDER BY f.id DESC", RecFiche.class).getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        String rech = recherche == null ? "" : recherche.trim().toLowerCase();
        for (RecFiche f : fiches) {
            if (segment != null && !segment.isEmpty() && !segment.equals(f.getSegmentCommercial())) { continue; }
            if (profil != null && !profil.isEmpty() && !profil.equals(f.getProfilPaiement())) { continue; }
            Client c = em.find(Client.class, f.getClientId());
            String nom = c == null ? "" : nz(c.getNomCompte());
            String num = c == null ? "" : nz(c.getNumeroClient());
            String ag = c == null ? "" : nz(c.getAgence());
            if (agence != null && !agence.isEmpty() && !agence.equalsIgnoreCase(ag)) { continue; }
            if (!rech.isEmpty() && !(nom.toLowerCase().contains(rech) || num.toLowerCase().contains(rech))) { continue; }
            Map<String, Object> s = situation(f.getClientId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("clientId", f.getClientId());
            m.put("nomCompte", nom);
            m.put("numeroClient", num);
            m.put("agence", ag);
            m.put("segmentCommercial", f.getSegmentCommercial());
            m.put("profilPaiement", f.getProfilPaiement());
            m.put("responsable", f.getResponsable());
            m.put("statut", f.getStatut());
            m.put("canalPrefere", f.getCanalPrefere());
            m.put("encoursInitial", s.get("encoursInitial"));
            m.put("totalFactures", s.get("totalFactures"));
            m.put("totalPaiements", s.get("totalPaiements"));
            m.put("solde", s.get("solde"));
            out.add(m);
        }
        return out;
    }

    private String nz(String s) { return s == null ? "" : s; }
}
