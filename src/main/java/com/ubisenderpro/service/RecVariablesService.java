package com.ubisenderpro.service;

import com.ubisenderpro.entity.Client;
import com.ubisenderpro.entity.RecCreance;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Résolution des variables « finance » des modèles de relance et personnalisation
 * du corps : {nom_client}, {nom_societe}, {solde}, {montant_du}, {jours_retard},
 * {numero_facture}, {date_echeance}. Accepte les formats {var} et {{var}}.
 */
@Stateless
public class RecVariablesService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private RecFicheService ficheService;
    @EJB
    private ParametreService parametreService;

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Variables résolues pour un client (sur la base de sa situation et de la facture la plus ancienne échue). */
    public Map<String, String> resoudre(Long clientId) {
        Map<String, String> v = new LinkedHashMap<>();
        Client c = em.find(Client.class, clientId);
        v.put("nom_client", c == null ? "" : nz(c.getNomCompte()));
        v.put("nom_societe", nz(parametreService.valeur("app.societe", "")));

        Map<String, Object> s = ficheService.situation(clientId);
        BigDecimal solde = (BigDecimal) s.get("solde");
        v.put("solde", montant(solde));
        v.put("montant_du", montant(solde));

        // Facture échue la plus ancienne (impayée par approximation : la plus ancienne échéance dépassée).
        List<RecCreance> echues = em.createQuery(
                "SELECT c FROM RecCreance c WHERE c.clientId = :id AND c.type = 'FACTURE' " +
                "AND c.dateEcheance IS NOT NULL AND c.dateEcheance < :auj ORDER BY c.dateEcheance ASC",
                RecCreance.class).setParameter("id", clientId).setParameter("auj", LocalDate.now())
                .setMaxResults(1).getResultList();
        if (!echues.isEmpty()) {
            RecCreance f = echues.get(0);
            v.put("numero_facture", nz(f.getNumero()));
            v.put("date_echeance", f.getDateEcheance() == null ? "" : f.getDateEcheance().format(DF));
            long jours = ChronoUnit.DAYS.between(f.getDateEcheance(), LocalDate.now());
            v.put("jours_retard", String.valueOf(Math.max(0, jours)));
        } else {
            v.put("numero_facture", "");
            v.put("date_echeance", "");
            v.put("jours_retard", "0");
        }
        return v;
    }

    /** Remplace {var} et {{var}} par les valeurs ; supprime les variables non résolues restantes. */
    public String personnaliser(String corps, Map<String, String> vars) {
        if (corps == null) { return ""; }
        String r = corps;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String val = e.getValue() == null ? "" : e.getValue();
            r = r.replace("{{" + e.getKey() + "}}", val).replace("{" + e.getKey() + "}", val);
        }
        // Nettoyage des éventuelles variables non reconnues.
        r = r.replaceAll("\\{\\{[^}]+\\}\\}", "").replaceAll("\\{[^}]+\\}", "");
        return r;
    }

    private String montant(BigDecimal b) {
        return b == null ? "0" : b.stripTrailingZeros().toPlainString();
    }

    private String nz(String s) { return s == null ? "" : s; }
}
