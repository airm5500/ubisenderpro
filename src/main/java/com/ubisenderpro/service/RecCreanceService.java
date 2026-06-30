package com.ubisenderpro.service;

import com.ubisenderpro.entity.RecCreance;
import com.ubisenderpro.entity.RecPaiement;
import com.ubisenderpro.entity.RecPromesse;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Créances (factures / avoirs), règlements et promesses de paiement d'un client.
 */
@Stateless
public class RecCreanceService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    /* ---------------- Créances (factures / avoirs) ---------------- */

    public List<RecCreance> listerCreances(Long clientId) {
        return em.createQuery(
                "SELECT c FROM RecCreance c WHERE c.clientId = :id ORDER BY c.dateEcheance DESC, c.id DESC",
                RecCreance.class).setParameter("id", clientId).getResultList();
    }

    public RecCreance creerCreance(RecCreance c) {
        validerCreance(c);
        if (c.getType() == null || c.getType().trim().isEmpty()) { c.setType("FACTURE"); }
        em.persist(c);
        return c;
    }

    public RecCreance modifierCreance(Long id, RecCreance data) {
        RecCreance ex = em.find(RecCreance.class, id);
        if (ex == null) { throw new IllegalArgumentException("Créance introuvable"); }
        validerCreance(data);
        ex.setType(data.getType() == null ? "FACTURE" : data.getType());
        ex.setNumero(data.getNumero());
        ex.setDateEmission(data.getDateEmission());
        ex.setDateEcheance(data.getDateEcheance());
        ex.setMontant(data.getMontant());
        ex.setStatut(data.getStatut());
        ex.setNotes(data.getNotes());
        ex.setUpdatedAt(LocalDateTime.now());
        return em.merge(ex);
    }

    public void supprimerCreance(Long id) {
        RecCreance c = em.find(RecCreance.class, id);
        if (c != null) { em.remove(c); }
    }

    private void validerCreance(RecCreance c) {
        if (c.getClientId() == null) { throw new ValidationException("clientId", "Le client est obligatoire."); }
        if (c.getMontant() == null) { throw new ValidationException("montant", "Le montant est obligatoire."); }
    }

    /* ---------------- Règlements ---------------- */

    public List<RecPaiement> listerPaiements(Long clientId) {
        return em.createQuery(
                "SELECT p FROM RecPaiement p WHERE p.clientId = :id ORDER BY p.datePaiement DESC, p.id DESC",
                RecPaiement.class).setParameter("id", clientId).getResultList();
    }

    public RecPaiement creerPaiement(RecPaiement p) {
        if (p.getClientId() == null) { throw new ValidationException("clientId", "Le client est obligatoire."); }
        if (p.getMontant() == null) { throw new ValidationException("montant", "Le montant est obligatoire."); }
        em.persist(p);
        return p;
    }

    public void supprimerPaiement(Long id) {
        RecPaiement p = em.find(RecPaiement.class, id);
        if (p != null) { em.remove(p); }
    }

    /* ---------------- Promesses de paiement ---------------- */

    public List<RecPromesse> listerPromesses(Long clientId) {
        return em.createQuery(
                "SELECT p FROM RecPromesse p WHERE p.clientId = :id ORDER BY p.datePromesse DESC, p.id DESC",
                RecPromesse.class).setParameter("id", clientId).getResultList();
    }

    public RecPromesse creerPromesse(RecPromesse p) {
        if (p.getClientId() == null) { throw new ValidationException("clientId", "Le client est obligatoire."); }
        if (p.getMontant() == null) { throw new ValidationException("montant", "Le montant est obligatoire."); }
        if (p.getStatut() == null || p.getStatut().trim().isEmpty()) { p.setStatut("EN_ATTENTE"); }
        em.persist(p);
        return p;
    }

    public RecPromesse modifierPromesse(Long id, RecPromesse data) {
        RecPromesse ex = em.find(RecPromesse.class, id);
        if (ex == null) { throw new IllegalArgumentException("Promesse introuvable"); }
        ex.setDatePromesse(data.getDatePromesse());
        if (data.getMontant() != null) { ex.setMontant(data.getMontant()); }
        if (data.getStatut() != null && !data.getStatut().trim().isEmpty()) { ex.setStatut(data.getStatut()); }
        ex.setNotes(data.getNotes());
        ex.setUpdatedAt(LocalDateTime.now());
        return em.merge(ex);
    }

    public void supprimerPromesse(Long id) {
        RecPromesse p = em.find(RecPromesse.class, id);
        if (p != null) { em.remove(p); }
    }
}
