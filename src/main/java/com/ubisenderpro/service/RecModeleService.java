package com.ubisenderpro.service;

import com.ubisenderpro.entity.RecModele;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Modèles de relance du module Recouvrement (CRUD).
 */
@Stateless
public class RecModeleService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<RecModele> lister() {
        return em.createQuery("SELECT m FROM RecModele m ORDER BY m.nom", RecModele.class).getResultList();
    }

    public List<RecModele> listerActifs() {
        return em.createQuery("SELECT m FROM RecModele m WHERE m.actif = true ORDER BY m.nom", RecModele.class)
                .getResultList();
    }

    public Optional<RecModele> parId(Long id) { return Optional.ofNullable(em.find(RecModele.class, id)); }

    public RecModele creer(RecModele m) {
        valider(m);
        if (m.getCode() == null || m.getCode().trim().isEmpty()) {
            m.setCode(Codes.generer("REL", this::codeExiste));
        } else if (codeExiste(m.getCode().trim())) {
            throw new ValidationException("code", "Un modèle avec le code « " + m.getCode().trim() + " » existe déjà.");
        }
        em.persist(m);
        return m;
    }

    public RecModele modifier(Long id, RecModele data) {
        RecModele ex = em.find(RecModele.class, id);
        if (ex == null) { throw new IllegalArgumentException("Modèle introuvable"); }
        valider(data);
        ex.setNom(data.getNom());
        ex.setType(data.getType());
        ex.setCanal(data.getCanal());
        ex.setSujet(data.getSujet());
        ex.setCorps(data.getCorps());
        ex.setNomModeleWhatsapp(data.getNomModeleWhatsapp());
        ex.setParamsCorps(data.getParamsCorps());
        ex.setActif(data.isActif());
        ex.setUpdatedAt(LocalDateTime.now());
        return em.merge(ex);
    }

    public void supprimer(Long id) { parId(id).ifPresent(em::remove); }

    private void valider(RecModele m) {
        if (m.getNom() == null || m.getNom().trim().isEmpty()) {
            throw new ValidationException("nom", "Le nom du modèle est obligatoire.");
        }
        if (m.getCorps() == null || m.getCorps().trim().isEmpty()) {
            throw new ValidationException("corps", "Le corps du message est obligatoire.");
        }
    }

    private boolean codeExiste(String code) {
        return !em.createQuery("SELECT m FROM RecModele m WHERE m.code = :c", RecModele.class)
                .setParameter("c", code).setMaxResults(1).getResultList().isEmpty();
    }
}
