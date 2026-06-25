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
     * Crée les modèles de message « promotion » prédéfinis absents (idempotent).
     * Exécuté dans une vraie transaction EJB (appel via proxy depuis Bootstrap).
     * @return nombre de modèles créés.
     */
    public int initModelesPromo() {
        int crees = 0;
        for (Map.Entry<String, String> entry : PromoTemplates.CORPS.entrySet()) {
            String cle = entry.getKey();
            Long n = em.createQuery(
                    "SELECT COUNT(m) FROM ModeleMessage m WHERE m.cleSysteme = :c", Long.class)
                    .setParameter("c", cle).getSingleResult();
            if (n != null && n > 0) { continue; }
            ModeleMessage m = new ModeleMessage();
            m.setNom(PromoTemplates.NOMS.get(cle));
            m.setTypeModele("PROMOTION");
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
