package com.ubisenderpro.service;

import com.ubisenderpro.entity.Opportunite;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

@Stateless
public class OpportuniteService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<Opportunite> lister(String statut, Long agentId) {
        StringBuilder jpql = new StringBuilder("SELECT o FROM Opportunite o WHERE 1=1");
        if (statut != null && !statut.isEmpty()) jpql.append(" AND o.statut = :s");
        if (agentId != null) jpql.append(" AND o.agentId = :a");
        jpql.append(" ORDER BY o.updatedAt DESC, o.createdAt DESC");
        var q = em.createQuery(jpql.toString(), Opportunite.class);
        if (statut != null && !statut.isEmpty()) q.setParameter("s", statut);
        if (agentId != null) q.setParameter("a", agentId);
        List<Opportunite> res = q.getResultList();
        renseignerLibelles(res);
        return res;
    }

    /** Renseigne les libellés d'affichage (nom du client / de l'agent) pour les cartes du pipeline. */
    private void renseignerLibelles(List<Opportunite> opps) {
        if (opps == null || opps.isEmpty()) { return; }
        java.util.Set<Long> clientIds = new java.util.HashSet<>();
        java.util.Set<Long> agentIds = new java.util.HashSet<>();
        java.util.Set<Long> contactIds = new java.util.HashSet<>();
        for (Opportunite o : opps) {
            if (o.getClientId() != null) { clientIds.add(o.getClientId()); }
            if (o.getAgentId() != null) { agentIds.add(o.getAgentId()); }
            if (o.getContactId() != null) { contactIds.add(o.getContactId()); }
        }
        // Client : nom, code et entreprise pour l'affichage « (code) nom entreprise ».
        java.util.Map<Long, Object[]> clients = new java.util.HashMap<>();
        if (!clientIds.isEmpty()) {
            for (Object[] r : em.createQuery(
                    "SELECT c.id, c.nomCompte, c.numeroClient, c.entreprise FROM Client c WHERE c.id IN :ids", Object[].class)
                    .setParameter("ids", clientIds).getResultList()) {
                clients.put((Long) r[0], r);
            }
        }
        java.util.Map<Long, String> agents = new java.util.HashMap<>();
        if (!agentIds.isEmpty()) {
            for (Object[] r : em.createQuery(
                    "SELECT u.id, u.nomComplet, u.login FROM Utilisateur u WHERE u.id IN :ids", Object[].class)
                    .setParameter("ids", agentIds).getResultList()) {
                String nom = r[1] != null ? (String) r[1] : (String) r[2];
                agents.put((Long) r[0], nom);
            }
        }
        java.util.Map<Long, String> contacts = new java.util.HashMap<>();
        if (!contactIds.isEmpty()) {
            for (Object[] r : em.createQuery(
                    "SELECT ct.id, ct.nomComplet FROM ClientContact ct WHERE ct.id IN :ids", Object[].class)
                    .setParameter("ids", contactIds).getResultList()) {
                contacts.put((Long) r[0], (String) r[1]);
            }
        }
        for (Opportunite o : opps) {
            if (o.getClientId() != null) {
                Object[] c = clients.get(o.getClientId());
                if (c != null) {
                    o.setClientNom((String) c[1]);
                    o.setClientCode((String) c[2]);
                    o.setClientEntreprise((String) c[3]);
                }
            }
            if (o.getAgentId() != null) { o.setAgentNom(agents.get(o.getAgentId())); }
            if (o.getContactId() != null) { o.setContactNom(contacts.get(o.getContactId())); }
        }
    }

    public Optional<Opportunite> parId(Long id) { return Optional.ofNullable(em.find(Opportunite.class, id)); }

    public Opportunite creer(Opportunite o) { em.persist(o); return o; }
    public Opportunite modifier(Opportunite o) { return em.merge(o); }

    public Opportunite changerStatut(Long id, String statut) {
        Opportunite o = em.find(Opportunite.class, id);
        if (o == null) return null;
        o.setStatut(statut);
        return em.merge(o);
    }
}
