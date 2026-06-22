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

    public PageResult<Client> rechercher(String recherche, String agence, String region,
                                         Long segmentationId, int offset, int limit) {
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
        if (segmentationId != null) {
            where.append(" AND c.segmentationId = :seg");
            params.add(new Object[]{"seg", segmentationId});
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
        return new PageResult<>(data, total);
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
        em.persist(client);
        return client;
    }

    public Client modifier(Client client) {
        valider(client, false);
        client.setUpdatedAt(LocalDateTime.now());
        return em.merge(client);
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
}
