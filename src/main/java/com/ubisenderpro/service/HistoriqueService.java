package com.ubisenderpro.service;

import com.ubisenderpro.dto.HistoriqueLigne;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Historique global des envois : agrège les messages sortants des discussions,
 * les destinataires de campagnes et les destinataires d'envois de masse
 * WhatsApp Web en une vue unifiée (lecture seule), triée par date décroissante.
 */
@Stateless
public class HistoriqueService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    /** Nombre de lignes lues par source avant fusion (borne le coût mémoire). */
    private static final int PAR_SOURCE = 500;

    /**
     * @param canal      filtre canal (API/WEB) ou null/"" pour tous
     * @param type       filtre type (DISCUSSION/CAMPAGNE/ENVOI_MASSE) ou null/"" pour tous
     * @param q          recherche libre (numéro, nom, libellé) ou null/"" pour tout
     * @param dateDebut  date min (yyyy-MM-dd) incluse ou null/"" pour aucune borne
     * @param dateFin    date max (yyyy-MM-dd) incluse ou null/"" pour aucune borne
     * @param limit      nombre maximal de lignes renvoyées (défaut 200)
     */
    public List<HistoriqueLigne> lister(String canal, String type, String q,
                                        String dateDebut, String dateFin, int limit) {
        List<HistoriqueLigne> lignes = new ArrayList<>();
        boolean tousTypes = vide(type);

        if (tousTypes || "DISCUSSION".equals(type)) { lignes.addAll(discussions()); }
        if (tousTypes || "CAMPAGNE".equals(type)) { lignes.addAll(campagnes()); }
        if (tousTypes || "ENVOI_MASSE".equals(type)) { lignes.addAll(envoisMasse()); }

        String canalF = vide(canal) ? null : canal.trim().toUpperCase();
        String recherche = vide(q) ? null : q.trim().toLowerCase();
        LocalDateTime debut = jour(dateDebut, false);
        LocalDateTime fin = jour(dateFin, true);

        List<HistoriqueLigne> filtrees = new ArrayList<>();
        for (HistoriqueLigne l : lignes) {
            if (canalF != null && !canalF.equals(l.getCanal())) { continue; }
            if (recherche != null && !correspond(l, recherche)) { continue; }
            if (debut != null && (l.getDate() == null || l.getDate().isBefore(debut))) { continue; }
            if (fin != null && (l.getDate() == null || l.getDate().isAfter(fin))) { continue; }
            filtrees.add(l);
        }

        filtrees.sort(Comparator.comparing(HistoriqueLigne::getDate,
                Comparator.nullsLast(Comparator.reverseOrder())));

        int max = limit <= 0 ? 200 : limit;
        return filtrees.size() > max ? filtrees.subList(0, max) : filtrees;
    }

    /** Convertit "yyyy-MM-dd" en début (00:00) ou fin (23:59:59) de journée ; null si vide/invalide. */
    private static LocalDateTime jour(String s, boolean finJournee) {
        if (vide(s)) { return null; }
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(s.trim().substring(0, 10));
            return finJournee ? d.atTime(23, 59, 59) : d.atStartOfDay();
        } catch (Exception e) { return null; }
    }

    private boolean correspond(HistoriqueLigne l, String r) {
        return contient(l.getNumero(), r) || contient(l.getNom(), r)
                || contient(l.getLibelle(), r) || contient(l.getApercu(), r);
    }

    @SuppressWarnings("unchecked")
    private List<HistoriqueLigne> discussions() {
        List<Object[]> rows = em.createQuery(
                "SELECT m.createdAt, c.canal, c.numeroWhatsapp, c.nomAffiche, m.contenu, " +
                "m.statut, m.erreur, m.id, m.expediteurId FROM Message m, Conversation c " +
                "WHERE m.conversationId = c.id AND m.direction = 'SORTANT' AND m.noteInterne = false " +
                "ORDER BY m.createdAt DESC")
                .setMaxResults(PAR_SOURCE).getResultList();
        Map<Long, String> users = utilisateurs();
        List<HistoriqueLigne> out = new ArrayList<>();
        for (Object[] r : rows) {
            String u = r[8] == null ? "" : users.getOrDefault((Long) r[8], "");
            out.add(new HistoriqueLigne("DISCUSSION", str(r[1], "API"), (Long) r[7], null, "Discussion",
                    str(r[2], ""), str(r[3], ""), u, apercu(r[4]), str(r[5], ""), str(r[6], null), (LocalDateTime) r[0]));
        }
        return out;
    }

    /** Cache id → nom complet des utilisateurs (pour la colonne « émetteur »). */
    @SuppressWarnings("unchecked")
    private Map<Long, String> utilisateurs() {
        Map<Long, String> m = new HashMap<>();
        List<Object[]> rows = em.createQuery(
                "SELECT u.id, u.nomComplet FROM Utilisateur u", Object[].class).getResultList();
        for (Object[] r : rows) { m.put((Long) r[0], str(r[1], "")); }
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<HistoriqueLigne> campagnes() {
        List<Object[]> rows = em.createQuery(
                "SELECT cd.envoyeAt, cd.createdAt, cd.numeroWhatsapp, cd.nomContact, cmp.nom, " +
                "cd.statut, cd.erreur, cd.id, cmp.id FROM CampagneDestinataire cd, Campagne cmp " +
                "WHERE cd.campagneId = cmp.id ORDER BY cd.createdAt DESC")
                .setMaxResults(PAR_SOURCE).getResultList();
        List<HistoriqueLigne> out = new ArrayList<>();
        for (Object[] r : rows) {
            LocalDateTime date = r[0] != null ? (LocalDateTime) r[0] : (LocalDateTime) r[1];
            out.add(new HistoriqueLigne("CAMPAGNE", "API", (Long) r[7], (Long) r[8], str(r[4], "Campagne"),
                    str(r[2], ""), str(r[3], ""), "", null, str(r[5], ""), str(r[6], null), date));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<HistoriqueLigne> envoisMasse() {
        List<Object[]> rows = em.createQuery(
                "SELECT d.sentAt, d.numero, d.nom, j.nom, d.statut, d.erreur, d.id, j.id " +
                "FROM WaBulkDestinataire d, WaBulkJob j WHERE d.jobId = j.id " +
                "ORDER BY d.id DESC")
                .setMaxResults(PAR_SOURCE).getResultList();
        List<HistoriqueLigne> out = new ArrayList<>();
        for (Object[] r : rows) {
            out.add(new HistoriqueLigne("ENVOI_MASSE", "WEB", (Long) r[6], (Long) r[7], str(r[3], "Envoi de masse"),
                    str(r[1], ""), str(r[2], ""), "", null, str(r[4], ""), str(r[5], null), (LocalDateTime) r[0]));
        }
        return out;
    }

    private static String apercu(Object o) {
        if (o == null) { return ""; }
        String s = String.valueOf(o);
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }

    private static boolean contient(String champ, String r) {
        return champ != null && champ.toLowerCase().contains(r);
    }

    private static boolean vide(String s) { return s == null || s.trim().isEmpty(); }

    private static String str(Object o, String defaut) { return o == null ? defaut : String.valueOf(o); }
}
