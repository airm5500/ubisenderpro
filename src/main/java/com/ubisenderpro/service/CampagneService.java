package com.ubisenderpro.service;

import com.ubisenderpro.entity.*;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Création, ciblage et lancement des campagnes (section 21 de la spec).
 * Le lancement envoie un message modèle à chaque destinataire valide via la
 * Cloud API, exclut les désabonnés et les numéros invalides, et suit les statuts.
 */
@Stateless
public class CampagneService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private SegmentService segmentService;
    @EJB
    private ListeService listeService;

    public List<Campagne> lister() { return lister(null); }

    /** Liste les campagnes, éventuellement filtrées par catégorie (ex. PROMOTION). */
    public List<Campagne> lister(String categorie) {
        if (categorie == null || categorie.trim().isEmpty()) {
            return em.createQuery("SELECT c FROM Campagne c ORDER BY c.createdAt DESC", Campagne.class)
                    .getResultList();
        }
        return em.createQuery(
                "SELECT c FROM Campagne c WHERE c.categorie = :cat ORDER BY c.createdAt DESC", Campagne.class)
                .setParameter("cat", categorie).getResultList();
    }

    public Optional<Campagne> parId(Long id) { return Optional.ofNullable(em.find(Campagne.class, id)); }

    /**
     * Tableau de performance (§18) : agrège les campagnes filtrées (période sur
     * la date de création, canal, catégorie) et calcule les taux.
     * @return { totaux:{...}, lignes:[{...}] }.
     */
    public Map<String, Object> performance(LocalDate du, LocalDate au, String canal, String categorie) {
        StringBuilder jpql = new StringBuilder("SELECT c FROM Campagne c WHERE 1=1");
        if (du != null) { jpql.append(" AND c.createdAt >= :du"); }
        if (au != null) { jpql.append(" AND c.createdAt < :au"); }
        if (canal != null && !canal.isEmpty()) { jpql.append(" AND c.canal = :canal"); }
        if (categorie != null && !categorie.isEmpty()) { jpql.append(" AND c.categorie = :cat"); }
        jpql.append(" ORDER BY c.createdAt DESC");

        Query q = em.createQuery(jpql.toString(), Campagne.class);
        if (du != null) { q.setParameter("du", du.atStartOfDay()); }
        if (au != null) { q.setParameter("au", au.plusDays(1).atStartOfDay()); }
        if (canal != null && !canal.isEmpty()) { q.setParameter("canal", canal); }
        if (categorie != null && !categorie.isEmpty()) { q.setParameter("cat", categorie); }

        @SuppressWarnings("unchecked")
        List<Campagne> camps = q.getResultList();

        List<Map<String, Object>> lignes = new ArrayList<>();
        long cibles = 0, envoyes = 0, distribues = 0, lus = 0, repondus = 0, echoues = 0;
        for (Campagne c : camps) {
            cibles += c.getNbDestinataires();
            envoyes += c.getNbEnvoyes();
            distribues += c.getNbDistribues();
            lus += c.getNbLus();
            repondus += c.getNbRepondus();
            echoues += c.getNbEchoues();
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("id", c.getId());
            l.put("nom", c.getNom());
            l.put("canal", c.getCanal());
            l.put("categorie", c.getCategorie());
            l.put("statut", c.getStatut());
            l.put("nbDestinataires", c.getNbDestinataires());
            l.put("nbEnvoyes", c.getNbEnvoyes());
            l.put("nbDistribues", c.getNbDistribues());
            l.put("nbLus", c.getNbLus());
            l.put("nbRepondus", c.getNbRepondus());
            l.put("nbEchoues", c.getNbEchoues());
            l.put("tauxDistribution", taux(c.getNbDistribues(), c.getNbEnvoyes()));
            l.put("tauxLecture", taux(c.getNbLus(), c.getNbEnvoyes()));
            l.put("tauxReponse", taux(c.getNbRepondus(), c.getNbEnvoyes()));
            lignes.add(l);
        }

        Map<String, Object> totaux = new LinkedHashMap<>();
        totaux.put("nbCampagnes", camps.size());
        totaux.put("cibles", cibles);
        totaux.put("envoyes", envoyes);
        totaux.put("distribues", distribues);
        totaux.put("lus", lus);
        totaux.put("repondus", repondus);
        totaux.put("echoues", echoues);
        totaux.put("tauxDistribution", taux(distribues, envoyes));
        totaux.put("tauxLecture", taux(lus, envoyes));
        totaux.put("tauxReponse", taux(repondus, envoyes));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("totaux", totaux);
        res.put("lignes", lignes);
        return res;
    }

    private int taux(long part, long total) {
        return total <= 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }

    public Campagne creer(Campagne c) {
        valider(c);
        em.persist(c);
        return c;
    }

    /** Contrôle des champs obligatoires de la campagne, messages clairs (#6). */
    private void valider(Campagne c) {
        if (c.getNom() == null || c.getNom().trim().isEmpty()) {
            throw new ValidationException("nom", "Le nom de la campagne est obligatoire.");
        }
        if (c.getCanal() == null || c.getCanal().trim().isEmpty()) {
            c.setCanal("API");
        } else if (!"API".equalsIgnoreCase(c.getCanal()) && !"WEB".equalsIgnoreCase(c.getCanal())) {
            throw new ValidationException("canal", "Canal d'envoi invalide : choisissez « API » ou « WEB ».");
        }
    }
    public Campagne modifier(Campagne c) {
        Campagne ex = em.find(Campagne.class, c.getId());
        if (ex != null) {
            c.setCreatedAt(ex.getCreatedAt());
            if (c.getCreePar() == null) { c.setCreePar(ex.getCreePar()); }
        }
        return em.merge(c);
    }

    /** Supprime une campagne et ses destinataires (la FK destinataire est purgée d'abord). */
    public void supprimer(Long id) {
        em.createQuery("DELETE FROM CampagneDestinataire d WHERE d.campagneId = :c")
                .setParameter("c", id).executeUpdate();
        parId(id).ifPresent(em::remove);
    }

    /**
     * Construit la liste des destinataires depuis la liste statique et/ou le segment
     * dynamique attachés à la campagne, en supprimant les doublons et les désabonnés.
     */
    public int construireDestinataires(Long campagneId) {
        Campagne c = em.find(Campagne.class, campagneId);
        if (c == null) return 0;

        // Purge des destinataires précédents.
        em.createQuery("DELETE FROM CampagneDestinataire d WHERE d.campagneId = :c")
                .setParameter("c", campagneId).executeUpdate();

        List<ClientContact> contacts = new ArrayList<>();
        if (c.getListeId() != null) contacts.addAll(listeService.contacts(c.getListeId()));
        if (c.getSegmentId() != null) contacts.addAll(segmentService.evaluer(c.getSegmentId()));
        if (c.getSegmentationId() != null) contacts.addAll(contactsParSegmentation(c.getSegmentationId()));

        java.util.Set<String> vus = new java.util.HashSet<>();
        int total = 0;
        for (ClientContact ct : contacts) {
            String numero = ct.getNumeroWhatsapp();
            CampagneDestinataire d = new CampagneDestinataire();
            d.setCampagneId(campagneId);
            d.setContactId(ct.getId());
            d.setNumeroWhatsapp(numero);
            d.setNomContact(ct.getNomComplet());

            if (ct.isDesabonne()) d.setStatut("DESABONNE");
            else if (numero == null || numero.isEmpty()) d.setStatut("NUMERO_INVALIDE");
            else if (!vus.add(numero)) continue; // doublon
            else d.setStatut("EN_ATTENTE");

            em.persist(d);
            total++;
        }
        c.setNbDestinataires(total);
        em.merge(c);
        return total;
    }

    /** Contacts WhatsApp des clients d'une segmentation donnée. */
    private List<ClientContact> contactsParSegmentation(Long segmentationId) {
        return em.createQuery(
                "SELECT ct FROM ClientContact ct, Client cl WHERE ct.clientId = cl.id " +
                "AND cl.segmentationId = :seg AND ct.numeroWhatsapp IS NOT NULL AND ct.numeroWhatsapp <> ''",
                ClientContact.class)
                .setParameter("seg", segmentationId).getResultList();
    }

    /**
     * Valide qu'une campagne est lançable (compte WhatsApp + modèle définis).
     * L'envoi effectif est délégué à {@link CampagneSenderAsync} en arrière-plan.
     */
    public void verifierLancable(Long campagneId) {
        Campagne c = em.find(Campagne.class, campagneId);
        if (c == null) throw new IllegalArgumentException("Campagne introuvable");
        if (c.getModeleId() == null) throw new IllegalArgumentException("Modèle de message non défini");
        if ("WEB".equalsIgnoreCase(c.getCanal())) {
            if (c.getWaWebSessionId() == null || c.getWaWebSessionId().isEmpty())
                throw new IllegalArgumentException("Session WhatsApp Web non définie");
        } else if (c.getWhatsappAccountId() == null) {
            throw new IllegalArgumentException("Compte WhatsApp non défini");
        }
    }

    public void changerStatut(Long campagneId, String statut) {
        parId(campagneId).ifPresent(c -> { c.setStatut(statut); em.merge(c); });
    }

    /** Liste des destinataires d'une campagne (détails : numéro, nom, statut, erreur). */
    public List<CampagneDestinataire> destinataires(Long campagneId) {
        return em.createQuery(
                "SELECT d FROM CampagneDestinataire d WHERE d.campagneId = :c ORDER BY d.id",
                CampagneDestinataire.class).setParameter("c", campagneId).getResultList();
    }

    /**
     * Réinitialise les destinataires en échec d'une campagne pour permettre une
     * relance : ECHOUE -> EN_ATTENTE (l'historique des tentatives est conservé).
     * Décrémente d'autant le compteur d'échecs de la campagne.
     * @return le nombre de destinataires remis en file d'attente.
     */
    public int reinitialiserEchecs(Long campagneId) {
        int reinities = em.createQuery(
                "UPDATE CampagneDestinataire d SET d.statut = 'EN_ATTENTE' " +
                "WHERE d.campagneId = :c AND d.statut = 'ECHOUE'")
                .setParameter("c", campagneId).executeUpdate();
        if (reinities > 0) {
            Campagne c = em.find(Campagne.class, campagneId);
            if (c != null) {
                c.setNbEchoues(Math.max(0, c.getNbEchoues() - reinities));
                em.merge(c);
            }
        }
        return reinities;
    }
}
