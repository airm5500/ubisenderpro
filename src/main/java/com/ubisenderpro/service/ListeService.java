package com.ubisenderpro.service;

import com.ubisenderpro.entity.ClientContact;
import com.ubisenderpro.entity.ListeDiffusion;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Stateless
public class ListeService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<ListeDiffusion> lister() {
        return em.createQuery("SELECT l FROM ListeDiffusion l ORDER BY l.nom", ListeDiffusion.class).getResultList();
    }

    public Optional<ListeDiffusion> parId(Long id) { return Optional.ofNullable(em.find(ListeDiffusion.class, id)); }

    public ListeDiffusion creer(ListeDiffusion l) { em.persist(l); return l; }
    public ListeDiffusion modifier(ListeDiffusion l) { return em.merge(l); }

    /**
     * Rattache un contact à une liste. Renvoie 1 si le membre a été ajouté,
     * 0 s'il y figurait déjà : l'INSERT IGNORE respecte l'unicité
     * (liste_id, contact_id) sans lever d'erreur sur doublon, et le compte
     * permet de distinguer « ajouté » de « déjà présent » dans les rapports.
     */
    public int ajouterContact(Long listeId, Long contactId, String source) {
        return em.createNativeQuery(
                "INSERT IGNORE INTO usp_liste_diffusion_contact (liste_id, contact_id, source, created_at) " +
                "VALUES (?1, ?2, ?3, NOW())")
                .setParameter(1, listeId).setParameter(2, contactId).setParameter(3, source)
                .executeUpdate();
    }

    public void retirerContact(Long listeId, Long contactId) {
        em.createNativeQuery(
                "DELETE FROM usp_liste_diffusion_contact WHERE liste_id = ?1 AND contact_id = ?2")
                .setParameter(1, listeId).setParameter(2, contactId).executeUpdate();
    }

    /** Retire tous les membres d'une liste. Renvoie le nombre de membres retirés. */
    public int vider(Long listeId) {
        return em.createNativeQuery(
                "DELETE FROM usp_liste_diffusion_contact WHERE liste_id = ?1")
                .setParameter(1, listeId).executeUpdate();
    }

    /**
     * Importe des clients dans une liste à partir d'un contenu texte (un code client
     * par ligne). Pour chaque client trouvé, ajoute son contact principal. Renvoie
     * un récapitulatif {ajoutes, introuvables, sansContact}.
     */
    public java.util.Map<String, Object> importerClients(Long listeId, String contenu) {
        List<String> codes = new java.util.ArrayList<>();
        if (contenu != null) {
            for (String ligne : contenu.split("\\r?\\n")) {
                codes.add(ligne == null ? "" : ligne.split("[;,\\t]")[0]);
            }
        }
        return ajouterParCodes(listeId, codes, false);
    }

    /** Vrai pour un intitulé de colonne collé par mégarde à la place d'un code. */
    private static boolean estEnTete(String code) {
        return code.equalsIgnoreCase("code") || code.equalsIgnoreCase("code_client")
                || code.equalsIgnoreCase("code client") || code.equalsIgnoreCase("numero_client");
    }

    /**
     * Ajoute à une liste le contact principal de chaque code client fourni.
     *
     * @param simulation si vrai, rien n'est écrit : le rapport permet de vérifier
     *                   le fichier avant de l'appliquer.
     * @return {lignesLues, ajoutes, dejaPresents, introuvables, sansContact,
     *         exemplesIntrouvables}
     */
    public java.util.Map<String, Object> ajouterParCodes(Long listeId, List<String> codes, boolean simulation) {
        int lues = 0, ajoutes = 0, dejaPresents = 0, introuvables = 0, sansContact = 0;
        List<String> exemples = new java.util.ArrayList<>();
        for (String brut : (codes == null ? java.util.Collections.<String>emptyList() : codes)) {
            String code = brut == null ? "" : brut.trim();
            if (code.isEmpty() || estEnTete(code)) { continue; }
            lues++;
            List<com.ubisenderpro.entity.Client> cl = em.createQuery(
                    "SELECT c FROM Client c WHERE c.numeroClient = :n", com.ubisenderpro.entity.Client.class)
                    .setParameter("n", code).setMaxResults(1).getResultList();
            if (cl.isEmpty()) {
                introuvables++;
                // Quelques exemples suffisent pour comprendre l'erreur (mauvaise
                // colonne choisie, codes d'un autre référentiel...).
                if (exemples.size() < 10) { exemples.add(code); }
                continue;
            }
            List<ClientContact> cc = em.createQuery(
                    "SELECT ct FROM ClientContact ct WHERE ct.clientId = :id " +
                    "ORDER BY ct.contactPrincipal DESC, ct.id ASC", ClientContact.class)
                    .setParameter("id", cl.get(0).getId()).setMaxResults(1).getResultList();
            if (cc.isEmpty()) { sansContact++; continue; }
            if (simulation) { ajoutes++; continue; }
            if (ajouterContact(listeId, cc.get(0).getId(), "IMPORT") > 0) { ajoutes++; } else { dejaPresents++; }
        }
        java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("lignesLues", lues);
        r.put("ajoutes", ajoutes);
        r.put("dejaPresents", dejaPresents);
        r.put("introuvables", introuvables);
        r.put("sansContact", sansContact);
        r.put("exemplesIntrouvables", exemples);
        return r;
    }

    @SuppressWarnings("unchecked")
    public List<ClientContact> contacts(Long listeId) {
        return em.createQuery(
                "SELECT ct FROM ClientContact ct WHERE ct.id IN " +
                "(SELECT lc.contactId FROM ListeDiffusionContact lc WHERE lc.listeId = :l)",
                ClientContact.class)
                .setParameter("l", listeId).getResultList();
    }
}
