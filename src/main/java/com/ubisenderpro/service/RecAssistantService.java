package com.ubisenderpro.service;

import com.ubisenderpro.entity.Client;
import com.ubisenderpro.entity.RecCreance;
import com.ubisenderpro.entity.RecFiche;
import com.ubisenderpro.entity.RecModele;
import com.ubisenderpro.entity.RecProposition;
import com.ubisenderpro.entity.RecPromesse;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assistant de recouvrement : analyse quotidienne des créances et génération de
 * propositions de relance (jamais d'envoi à l'aveugle). L'utilisateur valide
 * avant envoi. Une seule proposition « en attente » par client à la fois.
 */
@Stateless
public class RecAssistantService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private RecFicheService ficheService;
    @EJB
    private RecEnvoiService envoiService;

    // Seuils (jours).
    private static final int PREVENTIVE_J = 3;   // échéance à venir sous 3 jours
    private static final int DEUXIEME_J = 15;    // 2e relance au-delà de 15 j de retard
    private static final int CRITIQUE_J = 45;    // client critique au-delà de 45 j

    /** Analyse les fiches et crée les propositions manquantes. Renvoie le nombre créé. */
    public int genererPropositions() {
        int crees = 0;
        List<RecFiche> fiches = em.createQuery("SELECT f FROM RecFiche f", RecFiche.class).getResultList();
        LocalDate auj = LocalDate.now();
        for (RecFiche f : fiches) {
            Long clientId = f.getClientId();
            // Promesses échues -> marquées NON_TENUE.
            List<RecPromesse> promNonTenues = em.createQuery(
                    "SELECT p FROM RecPromesse p WHERE p.clientId = :id AND p.statut = 'EN_ATTENTE' " +
                    "AND p.datePromesse IS NOT NULL AND p.datePromesse < :auj", RecPromesse.class)
                    .setParameter("id", clientId).setParameter("auj", auj).getResultList();
            for (RecPromesse p : promNonTenues) { p.setStatut("NON_TENUE"); em.merge(p); }

            BigDecimal solde = (BigDecimal) ficheService.situation(clientId).get("solde");
            BigDecimal totalPaiements = (BigDecimal) ficheService.situation(clientId).get("totalPaiements");

            // Retards sur factures.
            int maxRetard = 0;
            Integer minAvant = null;
            for (RecCreance c : em.createQuery(
                    "SELECT c FROM RecCreance c WHERE c.clientId = :id AND c.type = 'FACTURE' AND c.dateEcheance IS NOT NULL",
                    RecCreance.class).setParameter("id", clientId).getResultList()) {
                long d = ChronoUnit.DAYS.between(c.getDateEcheance(), auj);
                if (d > 0) { maxRetard = Math.max(maxRetard, (int) d); }
                else { int avant = (int) -d; if (minAvant == null || avant < minAvant) { minAvant = avant; } }
            }

            boolean du = solde != null && solde.signum() > 0;
            String motif = null, priorite = "NORMALE";
            if (!promNonTenues.isEmpty() && du) { motif = "PROMESSE_NON_TENUE"; priorite = "CRITIQUE"; }
            else if (du && maxRetard > CRITIQUE_J) { motif = "CLIENT_CRITIQUE"; priorite = "CRITIQUE"; }
            else if (du && maxRetard > DEUXIEME_J) { motif = "DEUXIEME_RELANCE"; priorite = "HAUTE"; }
            else if (du && maxRetard >= 1) { motif = "FACTURE_ECHUE"; priorite = "HAUTE"; }
            else if (du && totalPaiements != null && totalPaiements.signum() > 0) { motif = "PAIEMENT_PARTIEL"; priorite = "NORMALE"; }
            else if (minAvant != null && minAvant <= PREVENTIVE_J) { motif = "RELANCE_PREVENTIVE"; priorite = "NORMALE"; }

            if (motif == null) { continue; }
            // Une seule proposition en attente par client.
            boolean existe = !em.createQuery(
                    "SELECT p FROM RecProposition p WHERE p.clientId = :id AND p.statut = 'PROPOSEE'", RecProposition.class)
                    .setParameter("id", clientId).setMaxResults(1).getResultList().isEmpty();
            if (existe) { continue; }

            RecProposition p = new RecProposition();
            p.setClientId(clientId);
            p.setMotif(motif);
            p.setPriorite(priorite);
            p.setJoursRetard(maxRetard);
            p.setMontant(solde);
            p.setCanalRecommande(f.getCanalPrefere() == null || f.getCanalPrefere().isEmpty() ? "WHATSAPP" : f.getCanalPrefere());
            p.setModeleId(modeleRecommande(motif));
            p.setStatut("PROPOSEE");
            p.setCle(clientId + ":" + motif);
            em.persist(p);
            crees++;
        }
        return crees;
    }

    private Long modeleRecommande(String motif) {
        String type;
        switch (motif) {
            case "RELANCE_PREVENTIVE": type = "RELANCE_PREVENTIVE"; break;
            case "FACTURE_ECHUE": type = "FACTURE_ECHUE"; break;
            case "DEUXIEME_RELANCE": case "PROMESSE_NON_TENUE": type = "IMPAYE"; break;
            case "CLIENT_CRITIQUE": type = "MISE_EN_DEMEURE"; break;
            default: type = "DIVERS"; break;
        }
        List<RecModele> l = em.createQuery(
                "SELECT m FROM RecModele m WHERE m.actif = true AND m.type = :t ORDER BY m.id", RecModele.class)
                .setParameter("t", type).setMaxResults(1).getResultList();
        return l.isEmpty() ? null : l.get(0).getId();
    }

    public List<Map<String, Object>> lister(String statut, String agence) {
        String st = (statut == null || statut.isEmpty()) ? "PROPOSEE" : statut;
        List<RecProposition> props = em.createQuery(
                "SELECT p FROM RecProposition p WHERE p.statut = :s ORDER BY p.priorite DESC, p.montant DESC, p.id DESC",
                RecProposition.class).setParameter("s", st).getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (RecProposition p : props) {
            Client c = em.find(Client.class, p.getClientId());
            // Cloisonnement : on n'expose que les clients de l'agence portée.
            if (agence != null && (c == null || !agence.equalsIgnoreCase(c.getAgence()))) { continue; }
            RecModele m = p.getModeleId() == null ? null : em.find(RecModele.class, p.getModeleId());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", p.getId());
            r.put("clientId", p.getClientId());
            r.put("nomCompte", c == null ? "" : c.getNomCompte());
            r.put("motif", p.getMotif());
            r.put("priorite", p.getPriorite());
            r.put("joursRetard", p.getJoursRetard());
            r.put("montant", p.getMontant());
            r.put("canalRecommande", p.getCanalRecommande());
            r.put("modeleId", p.getModeleId());
            r.put("modeleNom", m == null ? "" : m.getNom());
            out.add(r);
        }
        return out;
    }

    public void valider(Long id, Long modeleId, String canal, Long expediteurId, String login) {
        RecProposition p = em.find(RecProposition.class, id);
        if (p == null) { throw new IllegalArgumentException("Proposition introuvable"); }
        if (!"PROPOSEE".equals(p.getStatut())) {
            throw new ValidationException("statut", "Cette proposition n'est plus en attente.");
        }
        Long mid = modeleId != null ? modeleId : p.getModeleId();
        if (mid == null) {
            throw new ValidationException("modele", "Choisissez un modèle de relance.");
        }
        String c = canal != null && !canal.isEmpty() ? canal : p.getCanalRecommande();
        envoiService.envoyer(p.getClientId(), mid, c, expediteurId, login);
        p.setStatut("VALIDEE");
        p.setModeleId(mid);
        p.setUpdatedAt(LocalDateTime.now());
        em.merge(p);
    }

    public void rejeter(Long id) {
        RecProposition p = em.find(RecProposition.class, id);
        if (p != null) { p.setStatut("REJETEE"); p.setUpdatedAt(LocalDateTime.now()); em.merge(p); }
    }
}
