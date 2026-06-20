package com.ubisenderpro.repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.List;
import java.util.Optional;

/**
 * Opérations JPA communes à toutes les entités.
 */
public abstract class GenericRepository<T> {

    @PersistenceContext(unitName = "ubisenderproPU")
    protected EntityManager em;

    private final Class<T> type;

    protected GenericRepository(Class<T> type) {
        this.type = type;
    }

    public T save(T entity) {
        em.persist(entity);
        return entity;
    }

    public T update(T entity) {
        return em.merge(entity);
    }

    public Optional<T> findById(Long id) {
        return Optional.ofNullable(em.find(type, id));
    }

    public List<T> findAll(int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(type);
        Root<T> root = cq.from(type);
        cq.select(root);
        return em.createQuery(cq).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public long count() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        cq.select(cb.count(cq.from(type)));
        return em.createQuery(cq).getSingleResult();
    }

    public void delete(T entity) {
        em.remove(em.contains(entity) ? entity : em.merge(entity));
    }

    protected Optional<T> singleResult(TypedQuery<T> query) {
        List<T> list = query.setMaxResults(1).getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
