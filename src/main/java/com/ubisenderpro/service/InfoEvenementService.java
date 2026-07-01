package com.ubisenderpro.service;

import com.ubisenderpro.entity.InfoEvenement;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Gestion des communications d'information / alertes opérationnelles (CRUD,
 * statuts, duplication). Une communication envoyée n'est ni supprimée ni
 * modifiée (§6) : uniquement consultée, dupliquée ou archivée.
 */
@Stateless
public class InfoEvenementService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<InfoEvenement> lister() {
        return em.createQuery(
                "SELECT i FROM InfoEvenement i ORDER BY i.actif DESC, i.createdAt DESC", InfoEvenement.class)
                .getResultList();
    }

    /** Filtre par type, hors archivées/annulées (onglets producteurs). */
    public List<InfoEvenement> listerParType(String type) {
        if (type == null || type.isEmpty()) { return lister(); }
        return em.createQuery(
                "SELECT i FROM InfoEvenement i WHERE i.type = :t AND i.statut NOT IN ('ARCHIVEE','ANNULEE') " +
                "ORDER BY i.createdAt DESC", InfoEvenement.class)
                .setParameter("t", type).getResultList();
    }

    /** Filtre par plusieurs types (ex. onglet « Problèmes de livraison »). */
    public List<InfoEvenement> listerParTypes(List<String> types) {
        return em.createQuery(
                "SELECT i FROM InfoEvenement i WHERE i.type IN :ts AND i.statut NOT IN ('ARCHIVEE','ANNULEE') " +
                "ORDER BY i.createdAt DESC", InfoEvenement.class)
                .setParameter("ts", types).getResultList();
    }

    public List<InfoEvenement> listerParStatut(String statut) {
        if (statut == null || statut.isEmpty()) { return lister(); }
        return em.createQuery(
                "SELECT i FROM InfoEvenement i WHERE i.statut = :s ORDER BY i.createdAt DESC", InfoEvenement.class)
                .setParameter("s", statut).getResultList();
    }

    public List<InfoEvenement> listerEnCours() {
        return em.createQuery(
                "SELECT i FROM InfoEvenement i WHERE i.statut NOT IN ('ENVOYEE','ARCHIVEE','ANNULEE','EXPIREE') " +
                "ORDER BY i.createdAt DESC", InfoEvenement.class).getResultList();
    }

    public List<InfoEvenement> listerHistorique() {
        return em.createQuery(
                "SELECT i FROM InfoEvenement i WHERE i.statut IN ('ENVOYEE','ARCHIVEE','ANNULEE','EXPIREE','ECHOUEE') " +
                "ORDER BY i.updatedAt DESC, i.createdAt DESC", InfoEvenement.class).getResultList();
    }

    public Optional<InfoEvenement> parId(Long id) { return Optional.ofNullable(em.find(InfoEvenement.class, id)); }

    public Optional<InfoEvenement> parCode(String code) {
        if (code == null || code.trim().isEmpty()) { return Optional.empty(); }
        List<InfoEvenement> l = em.createQuery(
                "SELECT i FROM InfoEvenement i WHERE i.code = :c", InfoEvenement.class)
                .setParameter("c", code.trim()).setMaxResults(1).getResultList();
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    public InfoEvenement creer(InfoEvenement i) {
        // Code généré par défaut (modifiable) s'il n'a pas été saisi.
        if (i.getCode() == null || i.getCode().trim().isEmpty()) {
            i.setCode(Codes.generer("INFO", c -> parCode(c).isPresent()));
        }
        valider(i, true);
        if (i.getStatut() == null || i.getStatut().isEmpty()) { i.setStatut("BROUILLON"); }
        em.persist(i);
        return i;
    }

    public InfoEvenement modifier(InfoEvenement data) {
        InfoEvenement ex = em.find(InfoEvenement.class, data.getId());
        if (ex == null) { throw new IllegalArgumentException("Information introuvable"); }
        if ("ENVOYEE".equals(ex.getStatut())) {
            throw new ValidationException("statut", "Une communication déjà envoyée ne peut pas être modifiée (la dupliquer).");
        }
        if (data.getCode() != null && !data.getCode().equalsIgnoreCase(ex.getCode())) {
            parCode(data.getCode()).ifPresent(a -> {
                throw new ValidationException("code", "Une information avec le code « " + data.getCode() + " » existe déjà.");
            });
        }
        valider(data, false);
        data.setStatut(ex.getStatut());
        data.setCreatedAt(ex.getCreatedAt());
        if (data.getCreePar() == null || data.getCreePar().isEmpty()) { data.setCreePar(ex.getCreePar()); }
        data.setUpdatedAt(LocalDateTime.now());
        return em.merge(data);
    }

    private void valider(InfoEvenement i, boolean creation) {
        if (i.getCode() == null || i.getCode().trim().isEmpty()) {
            throw new ValidationException("code", "Le code de l'information est obligatoire.");
        }
        if (i.getTitre() == null || i.getTitre().trim().isEmpty()) {
            throw new ValidationException("titre", "Le titre est obligatoire.");
        }
        if (i.getType() == null || i.getType().trim().isEmpty()) {
            throw new ValidationException("type", "Le type d'information est obligatoire.");
        }
        if (creation && parCode(i.getCode()).isPresent()) {
            throw new ValidationException("code", "Une information avec le code « " + i.getCode() + " » existe déjà.");
        }
    }

    public void supprimer(Long id) {
        InfoEvenement i = em.find(InfoEvenement.class, id);
        if (i == null) { return; }
        if ("ENVOYEE".equals(i.getStatut())) { changerStatut(id, "ARCHIVEE"); return; }
        em.remove(i);
    }

    public InfoEvenement programmer(Long id) { return changerStatut(id, "PROGRAMMEE"); }
    public InfoEvenement annuler(Long id) { return changerStatut(id, "ANNULEE"); }
    public InfoEvenement archiver(Long id) { return changerStatut(id, "ARCHIVEE"); }

    private InfoEvenement changerStatut(Long id, String statut) {
        InfoEvenement i = em.find(InfoEvenement.class, id);
        if (i == null) { return null; }
        i.setStatut(statut);
        i.setActif(!"ANNULEE".equals(statut) && !"ARCHIVEE".equals(statut) && !"EXPIREE".equals(statut));
        i.setUpdatedAt(LocalDateTime.now());
        return em.merge(i);
    }

    public InfoEvenement dupliquer(Long id) {
        InfoEvenement s = em.find(InfoEvenement.class, id);
        if (s == null) { return null; }
        InfoEvenement c = new InfoEvenement();
        c.setCode(codeUnique(s.getCode()));
        c.setType(s.getType());
        c.setTitre(s.getTitre() + " (copie)");
        c.setMessage(s.getMessage());
        c.setPriorite(s.getPriorite());
        c.setSociete(s.getSociete());
        c.setAgence(s.getAgence());
        c.setTournee(s.getTournee());
        c.setAudience(s.getAudience());
        c.setSegmentationId(s.getSegmentationId());
        c.setSegmentationIds(s.getSegmentationIds());
        c.setListeId(s.getListeId());
        c.setCanal(s.getCanal());
        c.setDateEnvoi(s.getDateEnvoi());
        c.setDateFinValidite(s.getDateFinValidite());
        c.setResponsable(s.getResponsable());
        c.setDateLivraison(s.getDateLivraison());
        c.setCreneau(s.getCreneau());
        c.setHeureInitiale(s.getHeureInitiale());
        c.setNouvelleHeure(s.getNouvelleHeure());
        c.setCauseInterne(s.getCauseInterne());
        c.setCauseCommunicable(s.getCauseCommunicable());
        c.setDateResolution(s.getDateResolution());
        c.setJourFerie(s.getJourFerie());
        c.setDateGarde(s.getDateGarde());
        c.setHeureLimiteCommande(s.getHeureLimiteCommande());
        c.setConsignesLivraison(s.getConsignesLivraison());
        c.setPharmacienGarde(s.getPharmacienGarde());
        c.setTelephonePharmacien(s.getTelephonePharmacien());
        c.setStatut("BROUILLON");
        em.persist(c);
        return c;
    }

    private String codeUnique(String base) {
        String candidat = base + "-COPIE";
        int i = 1;
        while (parCode(candidat).isPresent()) { candidat = base + "-COPIE" + (++i); }
        return candidat;
    }
}
