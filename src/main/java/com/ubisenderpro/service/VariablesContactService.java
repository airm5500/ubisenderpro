package com.ubisenderpro.service;

import com.ubisenderpro.entity.SegmentationClient;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Résolution des variables « contact » d'un message (par destinataire), partagée
 * entre l'envoi de campagne et l'envoi en masse. Supporte les tokens [VARIABLE]
 * et {{variable}}, replie une formule de politesse dont le nom est absent et
 * garantit qu'aucune variable {{...}} brute n'apparaît au destinataire.
 */
@Stateless
public class VariablesContactService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private ParametreService parametreService;

    /** Table des variables disponibles pour un destinataire (numéro + nom éventuel). */
    public Map<String, String> resoudre(String numero, String nomDest) {
        String nom = nomDest == null ? "" : nomDest;
        String tel = "", email = "", nomCompte = "", segmentation = "", ville = "", region = "", civilite = "";

        try {
            List<Object[]> rows = em.createQuery(
                    "SELECT ct.nomComplet, ct.telephonePrincipal, ct.email, cl.nomCompte, " +
                    "cl.segmentationId, cl.ville, cl.region, ct.civilite FROM ClientContact ct, Client cl " +
                    "WHERE ct.clientId = cl.id AND ct.numeroWhatsapp = :n", Object[].class)
                    .setParameter("n", numero).setMaxResults(1).getResultList();
            if (!rows.isEmpty()) {
                Object[] r = rows.get(0);
                if ((nom == null || nom.isEmpty()) && r[0] != null) { nom = (String) r[0]; }
                tel = vide(r[1]); email = vide(r[2]); nomCompte = vide(r[3]);
                ville = vide(r[5]); region = vide(r[6]); civilite = vide(r[7]);
                if (r[4] != null) {
                    SegmentationClient s = em.find(SegmentationClient.class, (Long) r[4]);
                    if (s != null) { segmentation = s.getLibelle(); }
                }
            }
        } catch (Exception ignore) { /* destinataire hors base : variables client vides */ }

        String societeEmettrice = parametreService.valeur("app.societe", parametreService.valeur("app.nom", ""));

        Map<String, String> vars = new HashMap<>();
        vars.put("NOM", nom);
        vars.put("NAME", nom);
        vars.put("NOM_CONTACT", nom);
        vars.put("CIVILITE", civilite);
        vars.put("TELEPHONE", tel);
        vars.put("EMAIL", email);
        vars.put("NOM_COMPTE", nomCompte);
        vars.put("SOCIETE_CLIENT", nomCompte);
        vars.put("SEGMENTATION", segmentation);
        vars.put("VILLE", ville);
        vars.put("REGION", region);
        vars.put("SOCIETE", societeEmettrice);
        vars.put("TEL_SOCIETE", parametreService.valeur("app.societe_tel", ""));
        vars.put("SITE", parametreService.valeur("app.site", ""));
        vars.put("LIEN_COMMANDE", parametreService.valeur("app.lien_commande", ""));
        return vars;
    }

    /**
     * Personnalise un corps pour un destinataire : remplit {{1}}, les tokens
     * [VAR] et {{var}}, replie les formules à nom vide (« Cher(e) client(e), »)
     * et retire toute variable {{...}} non résolue.
     */
    public String personnaliser(String corps, String numero, String nomDest) {
        if (corps == null) { return ""; }
        Map<String, String> vars = resoudre(numero, nomDest);
        String out = corps.replace("{{1}}", vars.getOrDefault("NOM", ""));
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String val = e.getValue() == null ? "" : e.getValue();
            out = out.replace("[" + e.getKey() + "]", val)
                     .replace("{{" + e.getKey().toLowerCase() + "}}", val);
        }
        // Filet : aucune variable {{...}} résiduelle ne doit apparaître.
        out = out.replaceAll("\\{\\{[^}]+\\}\\}", "");
        // Nettoyage des espaces orphelins laissés par un repli (avant ponctuation, doublons).
        return out.replaceAll(" +([,.;])", "$1").replaceAll("[ \\t]{2,}", " ");
    }

    private String vide(Object o) { return o == null ? "" : o.toString(); }
}
