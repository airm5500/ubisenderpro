package com.ubisenderpro.service;

import com.ubisenderpro.entity.ClientContact;
import com.ubisenderpro.entity.ListeDiffusion;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Stateless
public class ListeService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<ListeDiffusion> lister() {
        return em.createQuery("SELECT l FROM ListeDiffusion l ORDER BY l.nom", ListeDiffusion.class).getResultList();
    }

    public Optional<ListeDiffusion> parId(Long id) { return Optional.ofNullable(em.find(ListeDiffusion.class, id)); }

    public ListeDiffusion creer(ListeDiffusion l) { em.persist(l); return l; }
    public ListeDiffusion modifier(ListeDiffusion l) { return em.merge(l); }

    public void ajouterContact(Long listeId, Long contactId, String source) {
        // INSERT IGNORE pour respecter l'unicité (liste_id, contact_id) sans erreur sur doublon.
        em.createNativeQuery(
                "INSERT IGNORE INTO usp_liste_diffusion_contact (liste_id, contact_id, source, created_at) " +
                "VALUES (?1, ?2, ?3, NOW())")
                .setParameter(1, listeId).setParameter(2, contactId).setParameter(3, source)
                .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public List<ClientContact> contacts(Long listeId) {
        return em.createQuery(
                "SELECT ct FROM ClientContact ct WHERE ct.id IN " +
                "(SELECT lc.contactId FROM ListeDiffusionContact lc WHERE lc.listeId = :l)",
                ClientContact.class)
                .setParameter("l", listeId).getResultList();
    }
}
