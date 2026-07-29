package com.ubisenderpro.service;

import com.ubisenderpro.entity.ModeleMessage;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Stateless
public class ModeleService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<ModeleMessage> lister() {
        return em.createQuery("SELECT m FROM ModeleMessage m ORDER BY m.nom", ModeleMessage.class).getResultList();
    }

    /**
     * Crée les modèles de message marketing prédéfinis absents (promo + dispo/rupture),
     * idempotent. Exécuté dans une vraie transaction EJB (appel via proxy depuis Bootstrap).
     * @return nombre de modèles créés.
     */
    public int initModelesMarketing() {
        int crees = 0;
        crees += seedModeles(PromoTemplates.NOMS, PromoTemplates.CORPS, PromoTemplates.TYPES);
        crees += seedModeles(DispoTemplates.NOMS, DispoTemplates.CORPS, DispoTemplates.TYPES);
        crees += seedModeles(InfoTemplates.NOMS, InfoTemplates.CORPS, InfoTemplates.TYPES);
        return crees;
    }

    private int seedModeles(Map<String, String> noms, Map<String, String> corps, Map<String, String> types) {
        int crees = 0;
        for (Map.Entry<String, String> entry : corps.entrySet()) {
            String cle = entry.getKey();
            Long n = em.createQuery(
                    "SELECT COUNT(m) FROM ModeleMessage m WHERE m.cleSysteme = :c", Long.class)
                    .setParameter("c", cle).getSingleResult();
            if (n != null && n > 0) { rafraichirSiJamaisModifie(cle, entry.getValue()); continue; }
            ModeleMessage m = new ModeleMessage();
            m.setNom(noms.get(cle));
            m.setTypeModele(types.get(cle));
            m.setCategorie("MARKETING");
            m.setLangue("fr");
            m.setCorps(entry.getValue());
            m.setCleSysteme(cle);
            m.setStatutApprobation("BROUILLON");
            m.setActif(true);
            em.persist(m);
            crees++;
        }
        return crees;
    }

    /**
     * Aligne un modèle prédéfini sur sa version livrée, <b>uniquement s'il n'a
     * jamais été modifié par un utilisateur</b> ({@code updatedAt} reste null
     * tant que personne ne l'a édité — seul {@link #modifier} le renseigne).
     *
     * <p>Sans cela, l'amélioration d'un gabarit livré ne profitait qu'aux
     * nouvelles installations : les bases existantes gardaient l'ancien texte,
     * puisque le semis ignore les clés déjà présentes. Les modèles retouchés
     * par le client ne sont jamais écrasés.</p>
     */
    private void rafraichirSiJamaisModifie(String cleSysteme, String corpsLivre) {
        if (corpsLivre == null) { return; }
        List<ModeleMessage> l = em.createQuery(
                "SELECT m FROM ModeleMessage m WHERE m.cleSysteme = :c", ModeleMessage.class)
                .setParameter("c", cleSysteme).setMaxResults(1).getResultList();
        if (l.isEmpty()) { return; }
        ModeleMessage m = l.get(0);
        if (m.getUpdatedAt() != null) { return; }              // personnalisé : on n'y touche pas
        if (corpsLivre.equals(m.getCorps())) { return; }       // déjà à jour
        m.setCorps(corpsLivre);
        em.merge(m);
    }

    public Optional<ModeleMessage> parId(Long id) { return Optional.ofNullable(em.find(ModeleMessage.class, id)); }

    public ModeleMessage creer(ModeleMessage m) { em.persist(m); return m; }
    public ModeleMessage modifier(ModeleMessage m) {
        ModeleMessage ex = em.find(ModeleMessage.class, m.getId());
        if (ex != null) { m.setCreatedAt(ex.getCreatedAt()); }
        m.setUpdatedAt(java.time.LocalDateTime.now());
        return em.merge(m);
    }
    public void supprimer(Long id) { parId(id).ifPresent(em::remove); }

    /** Remplace les variables {{cle}} d'un corps de modèle par leurs valeurs. */
    public static String fusionner(String corps, java.util.Map<String, String> variables) {
        if (corps == null) return "";
        String resultat = corps;
        for (var e : variables.entrySet()) {
            resultat = resultat.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return resultat;
    }
}
