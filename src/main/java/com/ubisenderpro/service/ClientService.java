package com.ubisenderpro.service;

import com.ubisenderpro.dto.PageResult;
import com.ubisenderpro.entity.Client;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Stateless
public class ClientService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @javax.ejb.EJB
    private ReferentielGeoService referentielGeoService;

    public PageResult<Client> rechercher(String recherche, String agence, String region, String commune,
                                         Long segmentationId, Boolean actif, int offset, int limit) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object[]> params = new ArrayList<>();
        if (recherche != null && !recherche.isEmpty()) {
            where.append(" AND (LOWER(c.nomCompte) LIKE :rech OR LOWER(c.numeroClient) LIKE :rech)");
            params.add(new Object[]{"rech", "%" + recherche.toLowerCase() + "%"});
        }
        if (agence != null && !agence.isEmpty()) {
            where.append(" AND c.agence = :agence");
            params.add(new Object[]{"agence", agence});
        }
        if (region != null && !region.isEmpty()) {
            where.append(" AND c.region = :region");
            params.add(new Object[]{"region", region});
        }
        if (commune != null && !commune.isEmpty()) {
            where.append(" AND c.commune = :commune");
            params.add(new Object[]{"commune", commune});
        }
        if (segmentationId != null) {
            where.append(" AND c.segmentationId = :seg");
            params.add(new Object[]{"seg", segmentationId});
        }
        if (actif != null) {
            where.append(" AND c.actif = :actif");
            params.add(new Object[]{"actif", actif});
        }

        TypedQuery<Client> q = em.createQuery(
                "SELECT c FROM Client c" + where + " ORDER BY c.nomCompte", Client.class);
        TypedQuery<Long> qc = em.createQuery(
                "SELECT COUNT(c) FROM Client c" + where, Long.class);
        for (Object[] p : params) {
            q.setParameter((String) p[0], p[1]);
            qc.setParameter((String) p[0], p[1]);
        }

        List<Client> data = q.setFirstResult(offset).setMaxResults(limit).getResultList();
        long total = qc.getSingleResult();
        renseignerTelephonePrincipal(data);
        return new PageResult<>(data, total);
    }

    /**
     * Sélecteur de clients (SCL) : tous les clients actifs (filtrables par recherche,
     * agence, région, segmentation) avec leur contact principal (id + numéro). Une
     * ligne par client — sert à constituer des destinataires / listes / sélections.
     */
    public List<java.util.Map<String, Object>> pourSelectionClients(String q, String agence,
                                                                    String region, Long segmentationId) {
        StringBuilder where = new StringBuilder(" WHERE c.actif = true");
        List<Object[]> params = new ArrayList<>();
        if (q != null && !q.trim().isEmpty()) {
            where.append(" AND (LOWER(c.nomCompte) LIKE :q OR LOWER(c.numeroClient) LIKE :q OR LOWER(c.entreprise) LIKE :q)");
            params.add(new Object[]{"q", "%" + q.trim().toLowerCase() + "%"});
        }
        if (agence != null && !agence.isEmpty()) { where.append(" AND c.agence = :ag"); params.add(new Object[]{"ag", agence}); }
        if (region != null && !region.isEmpty()) { where.append(" AND c.region = :reg"); params.add(new Object[]{"reg", region}); }
        if (segmentationId != null) { where.append(" AND c.segmentationId = :seg"); params.add(new Object[]{"seg", segmentationId}); }

        TypedQuery<Client> query = em.createQuery("SELECT c FROM Client c" + where + " ORDER BY c.nomCompte", Client.class);
        for (Object[] p : params) { query.setParameter((String) p[0], p[1]); }
        List<Client> clients = query.setMaxResults(5000).getResultList();

        // Contact principal (id + numéro joignable) par client, en une requête.
        java.util.Map<Long, com.ubisenderpro.entity.ClientContact> principal = new java.util.HashMap<>();
        List<Long> ids = new ArrayList<>();
        for (Client c : clients) { if (c.getId() != null) { ids.add(c.getId()); } }
        if (!ids.isEmpty()) {
            for (com.ubisenderpro.entity.ClientContact cc : em.createQuery(
                    "SELECT cc FROM ClientContact cc WHERE cc.clientId IN :ids " +
                    "ORDER BY cc.contactPrincipal DESC, cc.id ASC", com.ubisenderpro.entity.ClientContact.class)
                    .setParameter("ids", ids).getResultList()) {
                if (!principal.containsKey(cc.getClientId())) { principal.put(cc.getClientId(), cc); }
            }
        }
        List<java.util.Map<String, Object>> out = new ArrayList<>();
        for (Client c : clients) {
            com.ubisenderpro.entity.ClientContact cc = principal.get(c.getId());
            String numero = cc == null ? null
                    : (cc.getNumeroWhatsapp() != null && !cc.getNumeroWhatsapp().trim().isEmpty()
                        ? cc.getNumeroWhatsapp() : cc.getTelephonePrincipal());
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("clientId", c.getId());
            m.put("code", c.getNumeroClient());
            m.put("nom", c.getNomCompte());
            m.put("entreprise", c.getEntreprise());
            m.put("agence", c.getAgence());
            m.put("region", c.getRegion());
            m.put("contactId", cc == null ? null : cc.getId());
            m.put("numero", numero);
            out.add(m);
        }
        return out;
    }

    /**
     * Renseigne le téléphone du contact principal pour chaque client de la page
     * (une seule requête sur les contacts, contact principal prioritaire, repli
     * sur le numéro WhatsApp). Champ transient, affiché en liste.
     */
    private void renseignerTelephonePrincipal(List<Client> clients) {
        if (clients == null || clients.isEmpty()) { return; }
        List<Long> ids = new ArrayList<>();
        for (Client c : clients) { if (c.getId() != null) { ids.add(c.getId()); } }
        if (ids.isEmpty()) { return; }
        List<com.ubisenderpro.entity.ClientContact> contacts = em.createQuery(
                "SELECT cc FROM ClientContact cc WHERE cc.clientId IN :ids " +
                "ORDER BY cc.contactPrincipal DESC, cc.id ASC", com.ubisenderpro.entity.ClientContact.class)
                .setParameter("ids", ids).getResultList();
        java.util.Map<Long, String> parClient = new java.util.HashMap<>();
        for (com.ubisenderpro.entity.ClientContact cc : contacts) {
            if (parClient.containsKey(cc.getClientId())) { continue; } // 1er = principal (tri)
            String tel = cc.getTelephonePrincipal();
            if (tel == null || tel.trim().isEmpty()) { tel = cc.getNumeroWhatsapp(); }
            if (tel != null && !tel.trim().isEmpty()) { parClient.put(cc.getClientId(), tel.trim()); }
        }
        for (Client c : clients) { c.setTelephonePrincipal(parClient.get(c.getId())); }
    }

    public Optional<Client> parId(Long id) {
        return Optional.ofNullable(em.find(Client.class, id));
    }

    public Optional<Client> parNumero(String numeroClient) {
        List<Client> list = em.createQuery(
                "SELECT c FROM Client c WHERE c.numeroClient = :num", Client.class)
                .setParameter("num", numeroClient)
                .setMaxResults(1)
                .getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Client creer(Client client) {
        valider(client, true);
        canonicaliserGeo(client);
        em.persist(client);
        em.flush();
        appliquerNumerosEtNaissance(client, client.getNumeros(), client.getDateNaissance());
        return client;
    }

    public Client modifier(Client client) {
        valider(client, false);
        Client ex = em.find(Client.class, client.getId());
        if (ex == null) { throw new ValidationException("id", "Compte client introuvable."); }
        canonicaliserGeo(client);
        // Copie des champs éditables : created_at et actif (géré par activer/désactiver)
        // sont préservés ; updated_at est positionné par @PreUpdate.
        ex.setNumeroClient(client.getNumeroClient());
        ex.setNomCompte(client.getNomCompte());
        ex.setEntreprise(client.getEntreprise());
        ex.setAgence(client.getAgence());
        ex.setRegion(client.getRegion());
        ex.setTournee(client.getTournee());
        ex.setEmailPrincipal(client.getEmailPrincipal());
        ex.setSegmentationId(client.getSegmentationId());
        ex.setAdresse(client.getAdresse());
        ex.setVille(client.getVille());
        ex.setCommune(client.getCommune());
        ex.setPays(client.getPays());
        ex.setStatut(client.getStatut());
        ex.setNotes(client.getNotes());
        Client saved = em.merge(ex);
        appliquerNumerosEtNaissance(saved, client.getNumeros(), client.getDateNaissance());
        return saved;
    }

    /**
     * Applique une liste de numéros à un client existant (écran « Contacts » =
     * numéros seuls). Réutilise la même logique additive que la fiche client.
     */
    public void enregistrerNumeros(Long clientId, List<java.util.Map<String, Object>> numeros) {
        Client client = em.find(Client.class, clientId);
        if (client == null) { throw new ValidationException("id", "Compte client introuvable."); }
        appliquerNumerosEtNaissance(client, numeros, null);
    }

    /**
     * Crée/met à jour les contacts d'un client à partir des numéros saisis dans la
     * fiche (additif : rapproche par numéro, ne supprime jamais). Le 1er marqué
     * « principal » devient le contact principal ; la date de naissance est portée
     * par ce contact principal. Consentements cochés par défaut à la création.
     */
    private void appliquerNumerosEtNaissance(Client client, List<java.util.Map<String, Object>> numeros, String dateNaissance) {
        if (numeros == null) { return; }
        com.ubisenderpro.entity.ClientContact principal = null;
        com.ubisenderpro.entity.ClientContact premier = null;
        for (java.util.Map<String, Object> n : numeros) {
            if (n == null) { continue; }
            String num = n.get("numero") == null ? "" : String.valueOf(n.get("numero")).trim();
            if (num.isEmpty()) { continue; }
            boolean wa = !Boolean.FALSE.equals(n.get("whatsapp"));      // WhatsApp par défaut
            boolean princ = Boolean.TRUE.equals(n.get("principal"));
            List<com.ubisenderpro.entity.ClientContact> ex = em.createQuery(
                    "SELECT c FROM ClientContact c WHERE c.clientId = :cid AND " +
                    "(c.numeroWhatsapp = :n OR c.telephonePrincipal = :n)", com.ubisenderpro.entity.ClientContact.class)
                    .setParameter("cid", client.getId()).setParameter("n", num).setMaxResults(1).getResultList();
            com.ubisenderpro.entity.ClientContact ct = ex.isEmpty() ? new com.ubisenderpro.entity.ClientContact() : ex.get(0);
            boolean creation = ct.getId() == null;
            if (creation) {
                ct.setClientId(client.getId());
                ct.setConsentementWhatsapp(true);
                ct.setConsentRelationnel(true);
            }
            if (ct.getNomComplet() == null || ct.getNomComplet().trim().isEmpty()) { ct.setNomComplet(client.getNomCompte()); }
            ct.setTelephonePrincipal(num);
            ct.setNumeroWhatsapp(wa ? num : ct.getNumeroWhatsapp());
            if (creation) { em.persist(ct); } else { em.merge(ct); }
            if (premier == null) { premier = ct; }
            if (princ && principal == null) { principal = ct; }
        }
        if (principal == null) { principal = premier; }
        if (principal != null) {
            em.flush();
            // Un seul contact principal pour ce client.
            em.createQuery("UPDATE ClientContact c SET c.contactPrincipal = false WHERE c.clientId = :cid")
                    .setParameter("cid", client.getId()).executeUpdate();
            principal.setContactPrincipal(true);
            appliquerNaissance(principal, dateNaissance);
            em.merge(principal);
        }
    }

    private void appliquerNaissance(com.ubisenderpro.entity.ClientContact ct, String dateNaissance) {
        if (dateNaissance == null || dateNaissance.trim().isEmpty()) { return; }
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(dateNaissance.trim().substring(0, 10));
            ct.setJourNaissance(d.getDayOfMonth());
            ct.setMoisNaissance(d.getMonthValue());
            ct.setAnneeNaissance(d.getYear());
        } catch (RuntimeException ignore) { /* format invalide : ignoré */ }
    }

    /**
     * Aligne les champs géographiques sur les référentiels : valeur canonique
     * réutilisée si elle existe (insensible à la casse), sinon créée. Couvre à la
     * fois la saisie formulaire et l'import (qui passent par creer/modifier).
     */
    private void canonicaliserGeo(Client c) {
        c.setAgence(referentielGeoService.assurer("AGENCE", c.getAgence()));
        c.setRegion(referentielGeoService.assurer("REGION", c.getRegion()));
        c.setVille(referentielGeoService.assurer("VILLE", c.getVille()));
        c.setCommune(referentielGeoService.assurer("COMMUNE", c.getCommune()));
        c.setPays(referentielGeoService.assurer("PAYS", c.getPays()));
    }

    /** Contrôle des champs obligatoires/format du compte client, messages clairs (#6). */
    private void valider(Client c, boolean creation) {
        if (c.getNumeroClient() == null || c.getNumeroClient().trim().isEmpty()) {
            throw new ValidationException("numeroClient", "Le numéro client est obligatoire.");
        }
        if (c.getNomCompte() == null || c.getNomCompte().trim().isEmpty()) {
            throw new ValidationException("nomCompte", "Le nom du compte est obligatoire.");
        }
        String email = c.getEmailPrincipal();
        if (email != null && !email.trim().isEmpty()
                && !email.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ValidationException("emailPrincipal",
                    "L'adresse e-mail « " + email.trim() + " » n'est pas valide.");
        }
        // Unicité du numéro client (clé fonctionnelle).
        Optional<Client> existant = parNumero(c.getNumeroClient().trim());
        if (existant.isPresent() && (creation || !existant.get().getId().equals(c.getId()))) {
            throw new ValidationException("numeroClient",
                    "Le numéro client « " + c.getNumeroClient().trim() + " » est déjà utilisé par un autre compte.");
        }
    }

    public void supprimer(Long id) {
        parId(id).ifPresent(em::remove);
    }

    /** Active/désactive un compte client (#10). Le statut suit l'état actif. */
    public Client definirActif(Long id, boolean actif) {
        Client c = em.find(Client.class, id);
        if (c == null) { return null; }
        c.setActif(actif);
        c.setStatut(actif ? "ACTIF" : "INACTIF");
        c.setUpdatedAt(LocalDateTime.now());
        return em.merge(c);
    }

    /** Valeurs distinctes pour les filtres de tri (agences, régions, communes). */
    public java.util.Map<String, List<String>> facettes() {
        java.util.Map<String, List<String>> m = new java.util.LinkedHashMap<>();
        m.put("agences", distinct("agence"));
        m.put("regions", distinct("region"));
        m.put("communes", distinct("commune"));
        return m;
    }

    private List<String> distinct(String champ) {
        return em.createQuery("SELECT DISTINCT c." + champ + " FROM Client c WHERE c." + champ +
                " IS NOT NULL AND c." + champ + " <> '' ORDER BY c." + champ, String.class).getResultList();
    }
}
