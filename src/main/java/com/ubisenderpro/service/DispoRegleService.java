package com.ubisenderpro.service;

import com.ubisenderpro.entity.DispoRegle;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CRUD des règles de programmation du risque de rupture (§11).
 */
@Stateless
public class DispoRegleService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<DispoRegle> lister() {
        return em.createQuery("SELECT r FROM DispoRegle r ORDER BY r.jourMois", DispoRegle.class).getResultList();
    }

    public List<DispoRegle> listerActives(String type) {
        return em.createQuery(
                "SELECT r FROM DispoRegle r WHERE r.actif = true AND r.type = :t ORDER BY r.jourMois", DispoRegle.class)
                .setParameter("t", type).getResultList();
    }

    public DispoRegle creer(DispoRegle r) {
        valider(r);
        em.persist(r);
        return r;
    }

    public DispoRegle modifier(DispoRegle data) {
        DispoRegle r = em.find(DispoRegle.class, data.getId());
        if (r == null) { throw new IllegalArgumentException("Règle introuvable"); }
        valider(data);
        r.setLibelle(data.getLibelle());
        r.setType(data.getType());
        r.setJourMois(data.getJourMois());
        r.setHeure(data.getHeure());
        r.setAudience(data.getAudience());
        r.setCanal(data.getCanal());
        r.setActif(data.isActif());
        r.setUpdatedAt(LocalDateTime.now());
        return em.merge(r);
    }

    public void supprimer(Long id) {
        DispoRegle r = em.find(DispoRegle.class, id);
        if (r != null) { em.remove(r); }
    }

    private void valider(DispoRegle r) {
        if (r.getLibelle() == null || r.getLibelle().trim().isEmpty()) {
            throw new ValidationException("libelle", "Le libellé de la règle est obligatoire.");
        }
        if (r.getJourMois() < 1 || r.getJourMois() > 31) {
            throw new ValidationException("jourMois", "Le jour du mois doit être compris entre 1 et 31.");
        }
        if (r.getHeure() < 0 || r.getHeure() > 23) {
            throw new ValidationException("heure", "L'heure doit être comprise entre 0 et 23.");
        }
        if (r.getType() == null || r.getType().isEmpty()) { r.setType("RISQUE_RUPTURE"); }
    }
}
