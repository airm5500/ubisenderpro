package com.ubisenderpro.service;

import com.ubisenderpro.entity.Client;
import com.ubisenderpro.entity.RecCreance;
import com.ubisenderpro.entity.RecFiche;
import com.ubisenderpro.entity.RecPaiement;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Import CSV du module Recouvrement (séparateur « ; » ou tabulation, 1ʳᵉ ligne =
 * en-tête). Rattachement au client par {@code numero_client}.
 *
 * <ul>
 *   <li>Fiches (init) : numero_client; encours_initial; segment; profil; responsable; statut</li>
 *   <li>Créances : numero_client; type; numero; date_emission; date_echeance; montant; statut</li>
 *   <li>Règlements : numero_client; date_paiement; montant; mode; reference</li>
 * </ul>
 */
@Stateless
public class RecImportService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private ClientService clientService;

    private static final DateTimeFormatter[] DF = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy")
    };

    public Map<String, Object> importerFiches(String contenu) {
        int crees = 0, maj = 0, ignores = 0;
        for (Map<String, String> r : parser(contenu)) {
            Optional<Client> c = client(r);
            if (!c.isPresent()) { ignores++; continue; }
            Long cid = c.get().getId();
            RecFiche f = parClient(cid).orElseGet(RecFiche::new);
            boolean creation = f.getId() == null;
            f.setClientId(cid);
            if (r.containsKey("encours_initial")) { f.setEncoursInitial(montant(r.get("encours_initial"))); }
            if (r.containsKey("segment")) { f.setSegmentCommercial(r.get("segment")); }
            if (r.containsKey("profil")) { f.setProfilPaiement(r.get("profil")); }
            if (r.containsKey("responsable")) { f.setResponsable(r.get("responsable")); }
            if (r.containsKey("statut")) { f.setStatut(r.get("statut")); }
            if (creation) { em.persist(f); crees++; } else { em.merge(f); maj++; }
        }
        return rapport(crees, maj, ignores);
    }

    public Map<String, Object> importerCreances(String contenu) {
        int crees = 0, ignores = 0;
        for (Map<String, String> r : parser(contenu)) {
            Optional<Client> c = client(r);
            if (!c.isPresent()) { ignores++; continue; }
            RecCreance cr = new RecCreance();
            cr.setClientId(c.get().getId());
            cr.setType(vide(r.get("type")) ? "FACTURE" : r.get("type").trim().toUpperCase());
            cr.setNumero(r.get("numero"));
            cr.setDateEmission(date(r.get("date_emission")));
            cr.setDateEcheance(date(r.get("date_echeance")));
            cr.setMontant(montant(r.get("montant")));
            cr.setStatut(r.get("statut"));
            em.persist(cr);
            crees++;
        }
        return rapport(crees, 0, ignores);
    }

    public Map<String, Object> importerPaiements(String contenu) {
        int crees = 0, ignores = 0;
        for (Map<String, String> r : parser(contenu)) {
            Optional<Client> c = client(r);
            if (!c.isPresent()) { ignores++; continue; }
            RecPaiement p = new RecPaiement();
            p.setClientId(c.get().getId());
            p.setDatePaiement(date(r.get("date_paiement")));
            p.setMontant(montant(r.get("montant")));
            p.setMode(r.get("mode"));
            p.setReference(r.get("reference"));
            em.persist(p);
            crees++;
        }
        return rapport(crees, 0, ignores);
    }

    /* ----------------- utilitaires ----------------- */

    private Optional<RecFiche> parClient(Long clientId) {
        List<RecFiche> l = em.createQuery("SELECT f FROM RecFiche f WHERE f.clientId = :c", RecFiche.class)
                .setParameter("c", clientId).setMaxResults(1).getResultList();
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    private Optional<Client> client(Map<String, String> r) {
        String num = r.get("numero_client");
        if (num == null) { num = r.get("numeroclient"); }
        if (num == null || num.trim().isEmpty()) { return Optional.empty(); }
        return clientService.parNumero(num.trim());
    }

    /** Parse CSV (séparateur ; ou tabulation), 1ʳᵉ ligne = en-tête (noms de colonnes). */
    private List<Map<String, String>> parser(String contenu) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (contenu == null || contenu.trim().isEmpty()) { return rows; }
        String[] lignes = contenu.split("\\r?\\n");
        String[] entete = null;
        for (String ligne : lignes) {
            if (ligne == null || ligne.trim().isEmpty()) { continue; }
            String[] cols = ligne.split("[;\\t]", -1);
            for (int i = 0; i < cols.length; i++) { cols[i] = cols[i].trim(); }
            if (entete == null) {
                entete = new String[cols.length];
                for (int i = 0; i < cols.length; i++) {
                    entete[i] = cols[i].toLowerCase().replace(' ', '_').replace('é', 'e').replace('è', 'e');
                }
                continue;
            }
            Map<String, String> m = new LinkedHashMap<>();
            for (int i = 0; i < entete.length && i < cols.length; i++) {
                if (!entete[i].isEmpty()) { m.put(entete[i], cols[i]); }
            }
            rows.add(m);
        }
        return rows;
    }

    private boolean vide(String s) { return s == null || s.trim().isEmpty(); }

    private BigDecimal montant(String s) {
        if (vide(s)) { return BigDecimal.ZERO; }
        String v = s.trim().replace(" ", "").replace(",", ".");
        try { return new BigDecimal(v); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private LocalDate date(String s) {
        if (vide(s)) { return null; }
        String v = s.trim().length() >= 10 ? s.trim().substring(0, 10) : s.trim();
        for (DateTimeFormatter f : DF) {
            try { return LocalDate.parse(v, f); } catch (Exception ignore) { /* essai suivant */ }
        }
        return null;
    }

    private Map<String, Object> rapport(int crees, int maj, int ignores) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("crees", crees);
        m.put("misAJour", maj);
        m.put("ignores", ignores);
        return m;
    }
}
