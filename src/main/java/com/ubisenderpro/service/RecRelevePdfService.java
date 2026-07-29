package com.ubisenderpro.service;

import com.ubisenderpro.entity.Client;
import com.ubisenderpro.entity.RecCreance;
import com.ubisenderpro.entity.RecPaiement;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Relevé de compte PDF d'un client : en-tête société, situation (encours,
 * factures, avoirs, règlements, solde), détail des créances et paiements.
 * Sert de pièce jointe optionnelle aux relances (e-mail / WhatsApp).
 *
 * <p>L'édition est portée par les modèles JasperReports {@code releve.jrxml}
 * (maître) + {@code releve_creances.jrxml} / {@code releve_paiements.jrxml}
 * (sous-rapports) : la mise en page se personnalise dans le répertoire des
 * rapports, comme toutes les impressions. Ce service ne fait plus que réunir
 * et mettre en forme les données.</p>
 */
@Stateless
public class RecRelevePdfService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private RecFicheService ficheService;
    @EJB
    private RapportService rapportService;

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Nom de fichier suggéré pour le relevé d'un client. */
    public String nomFichier(Long clientId) {
        Client c = em.find(Client.class, clientId);
        String ref = c == null ? String.valueOf(clientId)
                : (c.getNumeroClient() != null && !c.getNumeroClient().isEmpty()
                    ? c.getNumeroClient() : String.valueOf(clientId));
        return "releve-compte-" + ref.replaceAll("[^A-Za-z0-9_-]", "_") + ".pdf";
    }

    /** Construit le relevé de compte PDF du client et renvoie le binaire. */
    public byte[] genererReleve(Long clientId) {
        Client client = em.find(Client.class, clientId);
        Map<String, Object> s = ficheService.situation(clientId);

        Map<String, Object> p = new HashMap<>();
        p.put("CLIENT_NOM", nz(client == null ? null : client.getNomCompte()));
        p.put("CLIENT_NUMERO", nz(client == null ? null : client.getNumeroClient()));
        p.put("CLIENT_AGENCE", nz(client == null ? null : client.getAgence()));
        p.put("ENCOURS_INITIAL", montant((BigDecimal) s.get("encoursInitial")));
        p.put("TOTAL_FACTURES", montant((BigDecimal) s.get("totalFactures")));
        p.put("TOTAL_AVOIRS", montant((BigDecimal) s.get("totalAvoirs")));
        p.put("TOTAL_PAIEMENTS", montant((BigDecimal) s.get("totalPaiements")));
        p.put("SOLDE", montant((BigDecimal) s.get("solde")));

        List<Map<String, ?>> creances = new ArrayList<>();
        for (RecCreance c : em.createQuery(
                "SELECT c FROM RecCreance c WHERE c.clientId = :id ORDER BY c.dateEcheance ASC, c.id ASC",
                RecCreance.class).setParameter("id", clientId).getResultList()) {
            Map<String, Object> l = RapportService.ligne();
            l.put("numero", nz(c.getNumero()));
            l.put("type", "AVOIR".equalsIgnoreCase(c.getType()) ? "Avoir" : "Facture");
            l.put("emission", c.getDateEmission() == null ? "" : c.getDateEmission().format(DF));
            l.put("echeance", c.getDateEcheance() == null ? "" : c.getDateEcheance().format(DF));
            l.put("montant", montant(c.getMontant()));
            creances.add(l);
        }
        List<Map<String, ?>> paiements = new ArrayList<>();
        for (RecPaiement pa : em.createQuery(
                "SELECT p FROM RecPaiement p WHERE p.clientId = :id ORDER BY p.datePaiement ASC, p.id ASC",
                RecPaiement.class).setParameter("id", clientId).getResultList()) {
            Map<String, Object> l = RapportService.ligne();
            l.put("date", pa.getDatePaiement() == null ? "" : pa.getDatePaiement().format(DF));
            l.put("mode", nz(pa.getMode()));
            l.put("reference", nz(pa.getReference()));
            l.put("montant", montant(pa.getMontant()));
            paiements.add(l);
        }
        p.put("LIGNES_CREANCES", creances);
        p.put("LIGNES_PAIEMENTS", paiements);
        p.put("SR_CREANCES", rapportService.modeleCompile("releve_creances"));
        p.put("SR_PAIEMENTS", rapportService.modeleCompile("releve_paiements"));

        // Le rapport maître n'a pas de bande détail : tout passe par les
        // paramètres et les deux sous-rapports.
        return rapportService.pdf("releve", p, java.util.Collections.emptyList());
    }

    /** 12 345,67 — même mise en forme que l'ancien relevé. */
    private String montant(BigDecimal b) {
        if (b == null) { return "0"; }
        return String.format("%,.2f", b).replace(',', ' ').replace('.', ',');
    }

    private String nz(String s) { return s == null ? "" : s; }
}
