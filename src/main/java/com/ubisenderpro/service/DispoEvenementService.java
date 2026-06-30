package com.ubisenderpro.service;

import com.ubisenderpro.entity.DispoEvenement;
import com.ubisenderpro.entity.DispoProduit;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Gestion des événements de disponibilité / rupture (CRUD, statuts, duplication).
 */
@Stateless
public class DispoEvenementService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private DispoProduitService produitService;

    public List<DispoEvenement> lister() {
        return em.createQuery(
                "SELECT e FROM DispoEvenement e ORDER BY e.actif DESC, e.createdAt DESC", DispoEvenement.class)
                .getResultList();
    }

    /** Filtre par type (onglets disponibles / retours / risques / ruptures). */
    public List<DispoEvenement> listerParType(String type) {
        if (type == null || type.isEmpty()) { return lister(); }
        return em.createQuery(
                "SELECT e FROM DispoEvenement e WHERE e.type = :t AND e.statut NOT IN ('ARCHIVEE','ANNULEE') " +
                "ORDER BY e.createdAt DESC", DispoEvenement.class)
                .setParameter("t", type).getResultList();
    }

    /** Filtre par statut (onglets « Annonces programmées », « Historique »). */
    public List<DispoEvenement> listerParStatut(String statut) {
        if (statut == null || statut.isEmpty()) { return lister(); }
        return em.createQuery(
                "SELECT e FROM DispoEvenement e WHERE e.statut = :s ORDER BY e.createdAt DESC", DispoEvenement.class)
                .setParameter("s", statut).getResultList();
    }

    /** Historique : événements envoyés, archivés ou annulés. */
    public List<DispoEvenement> listerHistorique() {
        return em.createQuery(
                "SELECT e FROM DispoEvenement e WHERE e.statut IN ('ENVOYEE','ARCHIVEE','ANNULEE') " +
                "ORDER BY e.updatedAt DESC, e.createdAt DESC", DispoEvenement.class)
                .getResultList();
    }

    public Optional<DispoEvenement> parId(Long id) { return Optional.ofNullable(em.find(DispoEvenement.class, id)); }

    public Optional<DispoEvenement> parCode(String code) {
        if (code == null || code.trim().isEmpty()) { return Optional.empty(); }
        List<DispoEvenement> l = em.createQuery(
                "SELECT e FROM DispoEvenement e WHERE e.code = :c", DispoEvenement.class)
                .setParameter("c", code.trim()).setMaxResults(1).getResultList();
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    public DispoEvenement creer(DispoEvenement e) {
        // Code généré par défaut (modifiable) s'il n'a pas été saisi.
        if (e.getCode() == null || e.getCode().trim().isEmpty()) {
            e.setCode(Codes.generer("DISPO", c -> parCode(c).isPresent()));
        }
        valider(e, true);
        if (e.getStatut() == null || e.getStatut().isEmpty()) { e.setStatut("BROUILLON"); }
        em.persist(e);
        return e;
    }

    public DispoEvenement modifier(DispoEvenement data) {
        DispoEvenement ex = em.find(DispoEvenement.class, data.getId());
        if (ex == null) { throw new IllegalArgumentException("Événement introuvable"); }
        // Unicité du code si modifié.
        if (data.getCode() != null && !data.getCode().equalsIgnoreCase(ex.getCode())) {
            parCode(data.getCode()).ifPresent(autre -> {
                throw new ValidationException("code", "Un événement avec le code « " + data.getCode() + " » existe déjà.");
            });
        }
        valider(data, false);
        ex.setCode(data.getCode());
        ex.setType(data.getType());
        ex.setTitre(data.getTitre());
        ex.setDescription(data.getDescription());
        ex.setDateDebut(data.getDateDebut());
        ex.setDateFin(data.getDateFin());
        ex.setAgence(data.getAgence());
        ex.setSociete(data.getSociete());
        ex.setAudience(data.getAudience());
        ex.setSegmentationId(data.getSegmentationId());
        ex.setCanal(data.getCanal());
        ex.setResponsable(data.getResponsable());
        ex.setUpdatedAt(LocalDateTime.now());
        return em.merge(ex);
    }

    private void valider(DispoEvenement e, boolean creation) {
        if (e.getCode() == null || e.getCode().trim().isEmpty()) {
            throw new ValidationException("code", "Le code de l'événement est obligatoire.");
        }
        if (e.getTitre() == null || e.getTitre().trim().isEmpty()) {
            throw new ValidationException("titre", "Le titre de l'événement est obligatoire.");
        }
        if (e.getType() == null || e.getType().trim().isEmpty()) {
            throw new ValidationException("type", "Le type d'événement est obligatoire.");
        }
        if (e.getDateDebut() != null && e.getDateFin() != null && e.getDateFin().isBefore(e.getDateDebut())) {
            throw new ValidationException("dateFin", "La date de fin doit être postérieure ou égale à la date de début.");
        }
        if (creation && parCode(e.getCode()).isPresent()) {
            throw new ValidationException("code", "Un événement avec le code « " + e.getCode() + " » existe déjà.");
        }
    }

    public void supprimer(Long id) {
        DispoEvenement e = em.find(DispoEvenement.class, id);
        if (e == null) { return; }
        // Un événement déjà envoyé n'est jamais supprimé physiquement -> archivage.
        if ("ENVOYEE".equals(e.getStatut())) { changerStatut(id, "ARCHIVEE"); return; }
        for (DispoProduit p : produitService.lister(id)) { produitService.supprimer(p.getId()); }
        em.remove(e);
    }

    public DispoEvenement programmer(Long id) { return changerStatut(id, "PROGRAMMEE"); }
    public DispoEvenement annuler(Long id) { return changerStatut(id, "ANNULEE"); }
    public DispoEvenement archiver(Long id) { return changerStatut(id, "ARCHIVEE"); }

    private DispoEvenement changerStatut(Long id, String statut) {
        DispoEvenement e = em.find(DispoEvenement.class, id);
        if (e == null) { return null; }
        e.setStatut(statut);
        e.setActif(!"ANNULEE".equals(statut) && !"ARCHIVEE".equals(statut));
        e.setUpdatedAt(LocalDateTime.now());
        return em.merge(e);
    }

    /** Duplique un événement (statut BROUILLON, code suffixé) avec ses produits. */
    public DispoEvenement dupliquer(Long id) {
        DispoEvenement src = em.find(DispoEvenement.class, id);
        if (src == null) { return null; }
        DispoEvenement c = new DispoEvenement();
        c.setCode(codeUnique(src.getCode()));
        c.setType(src.getType());
        c.setTitre(src.getTitre() + " (copie)");
        c.setDescription(src.getDescription());
        c.setDateDebut(src.getDateDebut());
        c.setDateFin(src.getDateFin());
        c.setAgence(src.getAgence());
        c.setSociete(src.getSociete());
        c.setAudience(src.getAudience());
        c.setSegmentationId(src.getSegmentationId());
        c.setCanal(src.getCanal());
        c.setResponsable(src.getResponsable());
        c.setStatut("BROUILLON");
        em.persist(c);
        for (DispoProduit p : produitService.lister(id)) {
            DispoProduit np = new DispoProduit();
            np.setEvenementId(c.getId());
            np.setArticleId(p.getArticleId());
            np.setCip7(p.getCip7());
            np.setCip13(p.getCip13());
            np.setNomProduit(p.getNomProduit());
            np.setQuantiteDisponible(p.getQuantiteDisponible());
            np.setSeuilRupture(p.getSeuilRupture());
            np.setCouvertureJours(p.getCouvertureJours());
            np.setDatePeremption(p.getDatePeremption());
            np.setNumeroLot(p.getNumeroLot());
            np.setAgence(p.getAgence());
            np.setStockLimite(p.isStockLimite());
            np.setLienReservation(p.getLienReservation());
            np.setStatut(p.getStatut());
            np.setActif(p.isActif());
            em.persist(np);
        }
        return c;
    }

    private String codeUnique(String base) {
        String candidat = base + "-COPIE";
        int i = 1;
        while (parCode(candidat).isPresent()) { candidat = base + "-COPIE" + (++i); }
        return candidat;
    }
}
