package com.ubisenderpro.service;

import com.ubisenderpro.entity.Commande;
import com.ubisenderpro.entity.CommandeDetail;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Stateless
public class CommandeService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<Commande> lister(String statut, int offset, int limit) {
        String jpql = "SELECT c FROM Commande c" +
                (statut != null && !statut.isEmpty() ? " WHERE c.statut = :s" : "") +
                " ORDER BY c.dateCommande DESC";
        var q = em.createQuery(jpql, Commande.class);
        if (statut != null && !statut.isEmpty()) q.setParameter("s", statut);
        return q.setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public Optional<Commande> parId(Long id) { return Optional.ofNullable(em.find(Commande.class, id)); }

    public List<CommandeDetail> details(Long commandeId) {
        return em.createQuery("SELECT d FROM CommandeDetail d WHERE d.commandeId = :c", CommandeDetail.class)
                .setParameter("c", commandeId).getResultList();
    }

    public Commande creer(Commande commande, List<CommandeDetail> lignes) {
        if (commande.getNumeroCommande() == null || commande.getNumeroCommande().isEmpty()) {
            commande.setNumeroCommande(genererNumero());
        }
        if (commande.getStatut() == null) commande.setStatut("BROUILLON");
        em.persist(commande);

        BigDecimal brut = BigDecimal.ZERO;
        BigDecimal remise = BigDecimal.ZERO;
        if (lignes != null) {
            for (CommandeDetail d : lignes) {
                d.setCommandeId(commande.getId());
                BigDecimal ligneBrut = d.getPrixUnitaire().multiply(d.getQuantite());
                BigDecimal ligneRemise = d.getRemise() == null ? BigDecimal.ZERO : d.getRemise();
                d.setMontantTotal(ligneBrut.subtract(ligneRemise));
                em.persist(d);
                brut = brut.add(ligneBrut);
                remise = remise.add(ligneRemise);
            }
        }
        commande.setMontantBrut(brut);
        commande.setMontantRemise(remise);
        commande.setMontantTotal(brut.subtract(remise));
        return em.merge(commande);
    }

    public Commande changerStatut(Long id, String statut) {
        Commande c = em.find(Commande.class, id);
        if (c == null) return null;
        c.setStatut(statut);
        return em.merge(c);
    }

    private String genererNumero() {
        String prefixe = "CMD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long n = em.createQuery(
                "SELECT COUNT(c) FROM Commande c WHERE c.numeroCommande LIKE :p", Long.class)
                .setParameter("p", prefixe + "%").getSingleResult();
        return prefixe + String.format("%04d", n + 1);
    }
}
