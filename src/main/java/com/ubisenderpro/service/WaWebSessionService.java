package com.ubisenderpro.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ubisenderpro.entity.WaWebSession;
import com.ubisenderpro.whatsapp.WaWebClient;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Gestion des sessions WhatsApp Web (canal non officiel). Délègue la connexion,
 * le QR et le statut au service compagnon Baileys via WaWebClient.
 */
@Stateless
public class WaWebSessionService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    private final WaWebClient client = new WaWebClient();

    public List<WaWebSession> lister() {
        return em.createQuery("SELECT s FROM WaWebSession s ORDER BY s.libelle", WaWebSession.class).getResultList();
    }

    public Optional<WaWebSession> parId(Long id) { return Optional.ofNullable(em.find(WaWebSession.class, id)); }

    public WaWebSession creer(WaWebSession s) { em.persist(s); return s; }

    public WaWebSession modifier(WaWebSession s) { return em.merge(s); }

    public void supprimer(Long id) {
        parId(id).ifPresent(s -> {
            try { client.logout(nodeId(id)); } catch (Exception ignore) { }
            em.remove(s);
        });
    }

    /** Identifiant de session côté service Node. */
    public static String nodeId(Long id) { return "acc-" + id; }

    /** Démarre/relance la session et met à jour le statut local. */
    public JsonNode demarrer(Long id) {
        WaWebSession s = em.find(WaWebSession.class, id);
        if (s == null) throw new IllegalArgumentException("Session introuvable");
        JsonNode node = client.start(nodeId(id));
        appliquerEtat(s, node);
        return node;
    }

    /** Rafraîchit et renvoie l'état courant (status, qr). */
    public JsonNode statut(Long id) {
        WaWebSession s = em.find(WaWebSession.class, id);
        if (s == null) throw new IllegalArgumentException("Session introuvable");
        JsonNode node = client.status(nodeId(id));
        appliquerEtat(s, node);
        return node;
    }

    public void deconnecter(Long id) {
        WaWebSession s = em.find(WaWebSession.class, id);
        if (s == null) return;
        try { client.logout(nodeId(id)); } catch (Exception ignore) { }
        s.setStatut("DECONNECTE");
        s.setUpdatedAt(LocalDateTime.now());
        em.merge(s);
    }

    private void appliquerEtat(WaWebSession s, JsonNode node) {
        if (node == null) return;
        String statut = node.path("status").asText(null);
        if (statut != null && !statut.isEmpty()) { s.setStatut(statut); }
        JsonNode me = node.path("me");
        if (me != null && me.has("id")) {
            String id = me.path("id").asText(null);
            if (id != null) { s.setNumero(id.split("[:@]")[0]); }
        }
        s.setUpdatedAt(LocalDateTime.now());
        em.merge(s);
    }
}
