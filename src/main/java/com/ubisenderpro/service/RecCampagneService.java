package com.ubisenderpro.service;

import com.ubisenderpro.entity.Client;
import com.ubisenderpro.entity.RecCreance;
import com.ubisenderpro.entity.RecFiche;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Campagnes de relance ciblées : sélection de clients (agence, responsable,
 * segment, profil, montant dû minimum, ancienneté minimum) puis envoi groupé
 * d'un modèle via un canal, en réutilisant le moteur d'envoi.
 */
@Stateless
public class RecCampagneService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private RecFicheService ficheService;
    @EJB
    private RecEnvoiService envoiService;

    /** Clients ciblés (avec solde et ancienneté) selon les critères. */
    public List<Map<String, Object>> cibler(String agence, String responsable, String segment,
                                            String profil, BigDecimal montantMin, Integer joursMin) {
        StringBuilder jpql = new StringBuilder("SELECT f FROM RecFiche f WHERE 1=1");
        if (notBlank(segment)) { jpql.append(" AND f.segmentCommercial = :seg"); }
        if (notBlank(profil)) { jpql.append(" AND f.profilPaiement = :prof"); }
        if (notBlank(responsable)) { jpql.append(" AND f.responsable = :resp"); }
        TypedQuery<RecFiche> q = em.createQuery(jpql.toString(), RecFiche.class);
        if (notBlank(segment)) { q.setParameter("seg", segment); }
        if (notBlank(profil)) { q.setParameter("prof", profil); }
        if (notBlank(responsable)) { q.setParameter("resp", responsable); }

        List<Map<String, Object>> out = new ArrayList<>();
        LocalDate auj = LocalDate.now();
        for (RecFiche f : q.getResultList()) {
            Client c = em.find(Client.class, f.getClientId());
            if (notBlank(agence) && (c == null || !agence.equalsIgnoreCase(c.getAgence()))) { continue; }
            BigDecimal solde = (BigDecimal) ficheService.situation(f.getClientId()).get("solde");
            if (montantMin != null && (solde == null || solde.compareTo(montantMin) < 0)) { continue; }
            int retard = maxRetard(f.getClientId(), auj);
            if (joursMin != null && retard < joursMin) { continue; }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("clientId", f.getClientId());
            m.put("nomCompte", c == null ? "" : c.getNomCompte());
            m.put("agence", c == null ? "" : c.getAgence());
            m.put("solde", solde);
            m.put("joursRetard", retard);
            out.add(m);
        }
        return out;
    }

    private int maxRetard(Long clientId, LocalDate auj) {
        List<RecCreance> l = em.createQuery(
                "SELECT c FROM RecCreance c WHERE c.clientId = :id AND c.type = 'FACTURE' " +
                "AND c.dateEcheance IS NOT NULL AND c.dateEcheance < :auj ORDER BY c.dateEcheance ASC",
                RecCreance.class).setParameter("id", clientId).setParameter("auj", auj).setMaxResults(1).getResultList();
        return l.isEmpty() ? 0 : (int) ChronoUnit.DAYS.between(l.get(0).getDateEcheance(), auj);
    }

    /** Envoi groupé : envoie le modèle à tous les clients ciblés. Renvoie un récapitulatif. */
    public Map<String, Object> envoyer(String agence, String responsable, String segment, String profil,
                                       BigDecimal montantMin, Integer joursMin,
                                       Long modeleId, String canal, Long expediteurId, String login) {
        List<Map<String, Object>> cibles = cibler(agence, responsable, segment, profil, montantMin, joursMin);
        int envoyes = 0, echecs = 0;
        for (Map<String, Object> cible : cibles) {
            Long clientId = (Long) cible.get("clientId");
            try {
                com.ubisenderpro.entity.RecEnvoi e = envoiService.envoyer(clientId, modeleId, canal, expediteurId, login);
                if ("ENVOYE".equals(e.getStatut())) { envoyes++; } else { echecs++; }
            } catch (Exception ex) {
                echecs++;
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("cibles", cibles.size());
        r.put("envoyes", envoyes);
        r.put("echecs", echecs);
        return r;
    }

    private boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }
}
