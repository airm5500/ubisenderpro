package com.ubisenderpro.service;

import com.ubisenderpro.entity.Parametre;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Paramètres globaux clé/valeur (table usp_parametre).
 * Sert notamment au « mode de fonctionnement » (API officielle / WhatsApp Web).
 */
@Stateless
public class ParametreService {

    /** Clé du mode d'envoi par défaut : API | WEB. */
    public static final String CLE_MODE = "whatsapp.mode_envoi";

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<Parametre> lister() {
        return em.createQuery("SELECT p FROM Parametre p ORDER BY p.categorie, p.cle", Parametre.class).getResultList();
    }

    public Parametre parCle(String cle) {
        List<Parametre> l = em.createQuery("SELECT p FROM Parametre p WHERE p.cle = :c", Parametre.class)
                .setParameter("c", cle).setMaxResults(1).getResultList();
        return l.isEmpty() ? null : l.get(0);
    }

    public String valeur(String cle, String defaut) {
        Parametre p = parCle(cle);
        return (p == null || p.getValeur() == null) ? defaut : p.getValeur();
    }

    /** Crée ou met à jour un paramètre. */
    public Parametre definir(String cle, String valeur, String description, String categorie) {
        Parametre p = parCle(cle);
        if (p == null) {
            p = new Parametre();
            p.setCle(cle);
            p.setDescription(description);
            p.setCategorie(categorie);
            p.setValeur(valeur);
            em.persist(p);
        } else {
            p.setValeur(valeur);
            if (description != null) p.setDescription(description);
            if (categorie != null) p.setCategorie(categorie);
            p.setUpdatedAt(LocalDateTime.now());
            p = em.merge(p);
        }
        return p;
    }
}
