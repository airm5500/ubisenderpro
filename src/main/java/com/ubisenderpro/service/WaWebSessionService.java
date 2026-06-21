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

    @javax.ejb.EJB
    private WaWebJournalService journal;

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

    /** Met à jour le statut d'une session (appelé sur événement du service Node). */
    public void enregistrerStatut(Long id, String statut) {
        WaWebSession s = em.find(WaWebSession.class, id);
        if (s != null) { s.setStatut(statut); s.setUpdatedAt(LocalDateTime.now()); em.merge(s); }
    }

    public void deconnecter(Long id) {
        WaWebSession s = em.find(WaWebSession.class, id);
        if (s == null) return;
        try { client.logout(nodeId(id)); } catch (Exception ignore) { }
        s.setStatut("DECONNECTE");
        s.setUpdatedAt(LocalDateTime.now());
        em.merge(s);
    }

    // ----- Filtre de numéros (Phase 3) -----
    public JsonNode verifierNumeros(Long id, List<String> numeros) {
        return client.checkNumbers(nodeId(id), numeros);
    }

    // ----- Envoi unitaire (composeur dual-canal) -----
    public java.util.Map<String, Object> envoyerTexte(Long id, String numero, String texte) {
        WaWebClient.SendResult r = client.sendText(nodeId(id), numero, texte);
        if (r.success) { journal.enregistrerSortant(id, numero, "TEXTE", texte, r.id); }
        return resultat(r);
    }

    public java.util.Map<String, Object> envoyerMedia(Long id, String numero, String type, String mediaUrl,
                                                      String caption, String mime, String nom) {
        WaWebClient.SendResult r = client.sendMedia(nodeId(id), numero, type, mediaUrl, caption, mime, nom);
        if (r.success) {
            String contenu = caption != null && !caption.isEmpty() ? caption : ("[" + (type == null ? "média" : type) + "]");
            journal.enregistrerSortant(id, numero, type, contenu, r.id);
        }
        return resultat(r);
    }

    private java.util.Map<String, Object> resultat(WaWebClient.SendResult r) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("success", r.success);
        m.put("id", r.id);
        m.put("erreur", r.erreur);
        return m;
    }

    // ----- Extraction (Phase 4) -----
    public JsonNode contacts(Long id) { return client.contacts(nodeId(id)); }
    public JsonNode groupes(Long id) { return client.groups(nodeId(id)); }
    public JsonNode participants(Long id, String jid) { return client.groupParticipants(nodeId(id), jid); }

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
