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
    /** Clé du préfixe pays (indicatif) : 1 à 4 chiffres. */
    public static final String CLE_PREFIXE = "whatsapp.prefixe_pays";

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

    /** Crée ou met à jour un paramètre (après contrôle des clés connues, #6). */
    public Parametre definir(String cle, String valeur, String description, String categorie) {
        valeur = valider(cle, valeur);
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

    /**
     * Contrôle (et normalise) la valeur des paramètres sensibles, messages clairs (#6).
     * @return la valeur éventuellement normalisée (ex. mode en majuscules).
     */
    private String valider(String cle, String valeur) {
        if (CLE_MODE.equals(cle)) {
            String v = valeur == null ? "" : valeur.trim().toUpperCase();
            if (!"API".equals(v) && !"WEB".equals(v)) {
                throw new ValidationException("valeur",
                        "Le mode d'envoi doit être « API » (Cloud API officielle) ou « WEB » (WhatsApp Web).");
            }
            return v;
        }
        if (CLE_PREFIXE.equals(cle)) {
            String v = valeur == null ? "" : valeur.trim();
            if (!v.matches("^[0-9]{1,4}$")) {
                throw new ValidationException("valeur",
                        "Le préfixe pays doit contenir de 1 à 4 chiffres (ex. 225).");
            }
            return v;
        }
        return valeur;
    }
}
