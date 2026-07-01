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

    public void retirerContact(Long listeId, Long contactId) {
        em.createNativeQuery(
                "DELETE FROM usp_liste_diffusion_contact WHERE liste_id = ?1 AND contact_id = ?2")
                .setParameter(1, listeId).setParameter(2, contactId).executeUpdate();
    }

    /**
     * Importe des clients dans une liste à partir d'un contenu texte (un code client
     * par ligne). Pour chaque client trouvé, ajoute son contact principal. Renvoie
     * un récapitulatif {ajoutes, introuvables, sansContact}.
     */
    public java.util.Map<String, Object> importerClients(Long listeId, String contenu) {
        int ajoutes = 0, introuvables = 0, sansContact = 0;
        if (contenu != null) {
            for (String ligne : contenu.split("\\r?\\n")) {
                String code = ligne == null ? "" : ligne.split("[;,\\t]")[0].trim();
                if (code.isEmpty() || code.equalsIgnoreCase("code") || code.equalsIgnoreCase("code_client")
                        || code.equalsIgnoreCase("numero_client")) { continue; }
                List<com.ubisenderpro.entity.Client> cl = em.createQuery(
                        "SELECT c FROM Client c WHERE c.numeroClient = :n", com.ubisenderpro.entity.Client.class)
                        .setParameter("n", code).setMaxResults(1).getResultList();
                if (cl.isEmpty()) { introuvables++; continue; }
                List<ClientContact> cc = em.createQuery(
                        "SELECT ct FROM ClientContact ct WHERE ct.clientId = :id " +
                        "ORDER BY ct.contactPrincipal DESC, ct.id ASC", ClientContact.class)
                        .setParameter("id", cl.get(0).getId()).setMaxResults(1).getResultList();
                if (cc.isEmpty()) { sansContact++; continue; }
                ajouterContact(listeId, cc.get(0).getId(), "IMPORT");
                ajoutes++;
            }
        }
        java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("ajoutes", ajoutes);
        r.put("introuvables", introuvables);
        r.put("sansContact", sansContact);
        return r;
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
