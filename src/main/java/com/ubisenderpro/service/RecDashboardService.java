package com.ubisenderpro.service;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tableaux de bord du module Recouvrement : agrégats par agence et consolidé
 * groupe (encours, encaissé, solde, taux de recouvrement, factures échues).
 * Requêtes natives agrégées (jointure usp_client pour l'agence).
 */
@Stateless
public class RecDashboardService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    private static final String Q_ENCOURS =
        "SELECT cl.agence, COALESCE(SUM(f.encours_initial),0) FROM usp_rec_fiche f " +
        "JOIN usp_client cl ON cl.id = f.client_id GROUP BY cl.agence";
    private static final String Q_FACTURES =
        "SELECT cl.agence, COALESCE(SUM(c.montant),0) FROM usp_rec_creance c " +
        "JOIN usp_client cl ON cl.id = c.client_id WHERE c.type = 'FACTURE' GROUP BY cl.agence";
    private static final String Q_AVOIRS =
        "SELECT cl.agence, COALESCE(SUM(c.montant),0) FROM usp_rec_creance c " +
        "JOIN usp_client cl ON cl.id = c.client_id WHERE c.type = 'AVOIR' GROUP BY cl.agence";
    private static final String Q_PAIEMENTS =
        "SELECT cl.agence, COALESCE(SUM(p.montant),0) FROM usp_rec_paiement p " +
        "JOIN usp_client cl ON cl.id = p.client_id GROUP BY cl.agence";
    private static final String Q_CLIENTS =
        "SELECT cl.agence, COUNT(*) FROM usp_rec_fiche f " +
        "JOIN usp_client cl ON cl.id = f.client_id GROUP BY cl.agence";
    private static final String Q_ECHUES =
        "SELECT cl.agence, COUNT(*) FROM usp_rec_creance c " +
        "JOIN usp_client cl ON cl.id = c.client_id WHERE c.type = 'FACTURE' " +
        "AND c.date_echeance IS NOT NULL AND c.date_echeance < CURDATE() GROUP BY cl.agence";
    private static final String Q_PROMESSES =
        "SELECT cl.agence, COUNT(*) FROM usp_rec_promesse p " +
        "JOIN usp_client cl ON cl.id = p.client_id WHERE p.statut = 'EN_ATTENTE' GROUP BY cl.agence";

    /** Point par agence (filtré sur une agence si fournie). */
    public List<Map<String, Object>> parAgence(String filtreAgence) {
        Map<String, BigDecimal> encours = agg(Q_ENCOURS), factures = agg(Q_FACTURES), avoirs = agg(Q_AVOIRS),
                paiements = agg(Q_PAIEMENTS), clients = agg(Q_CLIENTS), echues = agg(Q_ECHUES), promesses = agg(Q_PROMESSES);
        Set<String> agences = new LinkedHashSet<>();
        agences.addAll(clients.keySet()); agences.addAll(encours.keySet()); agences.addAll(factures.keySet());
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (String ag : agences) {
            if (filtreAgence != null && !filtreAgence.equalsIgnoreCase(ag)) { continue; }
            out.add(ligne(ag, encours, factures, avoirs, paiements, clients, echues, promesses));
        }
        return out;
    }

    /** Consolidé groupe (ou agence unique si filtre). */
    public Map<String, Object> groupe(String filtreAgence) {
        BigDecimal e = BigDecimal.ZERO, f = BigDecimal.ZERO, a = BigDecimal.ZERO, p = BigDecimal.ZERO,
                cl = BigDecimal.ZERO, ec = BigDecimal.ZERO, pr = BigDecimal.ZERO;
        for (Map<String, Object> l : parAgence(filtreAgence)) {
            e = e.add((BigDecimal) l.get("encours"));
            f = f.add((BigDecimal) l.get("factures"));
            a = a.add((BigDecimal) l.get("avoirs"));
            p = p.add((BigDecimal) l.get("recouvre"));
            cl = cl.add(new BigDecimal(l.get("clients").toString()));
            ec = ec.add(new BigDecimal(l.get("facturesEchues").toString()));
            pr = pr.add(new BigDecimal(l.get("promesses").toString()));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        BigDecimal solde = e.add(f).subtract(a).subtract(p);
        BigDecimal base = e.add(f).subtract(a);
        m.put("encours", e);
        m.put("factures", f);
        m.put("avoirs", a);
        m.put("recouvre", p);
        m.put("solde", solde);
        m.put("clients", cl.intValue());
        m.put("facturesEchues", ec.intValue());
        m.put("promesses", pr.intValue());
        m.put("tauxRecouvrement", taux(p, base));
        return m;
    }

    private Map<String, Object> ligne(String ag, Map<String, BigDecimal> encours, Map<String, BigDecimal> factures,
                                      Map<String, BigDecimal> avoirs, Map<String, BigDecimal> paiements,
                                      Map<String, BigDecimal> clients, Map<String, BigDecimal> echues,
                                      Map<String, BigDecimal> promesses) {
        BigDecimal e = encours.getOrDefault(ag, BigDecimal.ZERO), f = factures.getOrDefault(ag, BigDecimal.ZERO),
                a = avoirs.getOrDefault(ag, BigDecimal.ZERO), p = paiements.getOrDefault(ag, BigDecimal.ZERO);
        BigDecimal solde = e.add(f).subtract(a).subtract(p);
        BigDecimal base = e.add(f).subtract(a);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("agence", ag);
        m.put("encours", e);
        m.put("factures", f);
        m.put("avoirs", a);
        m.put("recouvre", p);
        m.put("solde", solde);
        m.put("clients", clients.getOrDefault(ag, BigDecimal.ZERO).intValue());
        m.put("facturesEchues", echues.getOrDefault(ag, BigDecimal.ZERO).intValue());
        m.put("promesses", promesses.getOrDefault(ag, BigDecimal.ZERO).intValue());
        m.put("tauxRecouvrement", taux(p, base));
        return m;
    }

    private BigDecimal taux(BigDecimal recouvre, BigDecimal base) {
        if (base == null || base.signum() <= 0) { return BigDecimal.ZERO; }
        return recouvre.multiply(new BigDecimal(100)).divide(base, 1, RoundingMode.HALF_UP);
    }

    @SuppressWarnings("unchecked")
    private Map<String, BigDecimal> agg(String sql) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        List<Object[]> rows = em.createNativeQuery(sql).getResultList();
        for (Object[] r : rows) {
            String ag = r[0] == null ? "(non renseignée)" : r[0].toString();
            BigDecimal v = r[1] == null ? BigDecimal.ZERO : new BigDecimal(r[1].toString());
            m.put(ag, v);
        }
        return m;
    }
}
