package com.ubisenderpro.service;

import com.ubisenderpro.entity.Automatisation;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

@Stateless
public class AutomatisationService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<Automatisation> lister() {
        return em.createQuery("SELECT a FROM Automatisation a ORDER BY a.nom", Automatisation.class).getResultList();
    }

    public Optional<Automatisation> parId(Long id) { return Optional.ofNullable(em.find(Automatisation.class, id)); }

    public Automatisation creer(Automatisation a) { em.persist(a); return a; }
    public Automatisation modifier(Automatisation a) { return em.merge(a); }
    public void supprimer(Long id) { parId(id).ifPresent(em::remove); }
}
