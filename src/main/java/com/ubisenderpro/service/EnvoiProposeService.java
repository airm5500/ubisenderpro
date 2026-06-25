package com.ubisenderpro.service;

import com.ubisenderpro.entity.Campagne;
import com.ubisenderpro.entity.EnvoiPropose;
import com.ubisenderpro.entity.MediaFichier;
import com.ubisenderpro.entity.ModeleMessage;
import com.ubisenderpro.entity.Promotion;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Calendrier marketing : génère des <b>propositions d'envoi</b> à partir des
 * promotions (annonce mensuelle, lancement, rappels J-3 / J-1) et gère leur
 * cycle de vie. Aucune proposition ne devient une campagne sans validation
 * humaine ({@link #valider}).
 */
@Stateless
public class EnvoiProposeService {

    /** Décalages (en jours avant la date de fin) des rappels générés. */
    private static final int[] RAPPELS_JOURS = { 3, 1 };
    private static final Locale FR = Locale.FRENCH;
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Détecte une variable {{...}} non remplie (filet de sécurité anti-variable-vide). */
    private static final Pattern VARIABLE_RESTANTE = Pattern.compile("\\{\\{[^}]+\\}\\}");

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private PromotionService promotionService;
    @EJB
    private CampagneService campagneService;
    @EJB
    private PromotionProduitService promotionProduitService;
    @EJB
    private PromotionXlsxService xlsxService;
    @EJB
    private MediaFichierService mediaFichierService;
    @EJB
    private ModeleService modeleService;

    /* ====================== Lecture ====================== */

    public List<EnvoiPropose> lister(String statut) {
        if (statut == null || statut.isEmpty()) {
            return em.createQuery(
                    "SELECT e FROM EnvoiPropose e ORDER BY e.datePrevue, e.type", EnvoiPropose.class)
                    .getResultList();
        }
        return em.createQuery(
                "SELECT e FROM EnvoiPropose e WHERE e.statut = :s ORDER BY e.datePrevue, e.type", EnvoiPropose.class)
                .setParameter("s", statut).getResultList();
    }

    public Optional<EnvoiPropose> parId(Long id) { return Optional.ofNullable(em.find(EnvoiPropose.class, id)); }

    private Optional<EnvoiPropose> parCle(String cle) {
        List<EnvoiPropose> l = em.createQuery(
                "SELECT e FROM EnvoiPropose e WHERE e.cle = :c", EnvoiPropose.class)
                .setParameter("c", cle).setMaxResults(1).getResultList();
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    /* ====================== Génération ====================== */

    /**
     * Génère/actualise les propositions à venir (idempotent : une proposition
     * existante n'est jamais dupliquée, et une décision humaine n'est jamais écrasée).
     *
     * @return le nombre de propositions créées.
     */
    public int genererPropositions() {
        LocalDate today = LocalDate.now();
        int crees = 0;

        // 1) Annonce mensuelle : une proposition pour le mois en cours s'il y a
        //    au moins une promotion active sur ce mois.
        crees += genererAnnonceMensuelle(today);

        // 2) Par promotion non terminée et non annulée/archivée : lancement + rappels.
        for (Promotion p : promotionService.lister()) {
            String st = p.getStatut();
            if ("ANNULEE".equals(st) || "ARCHIVEE".equals(st)) { continue; }

            // Lancement : à la date de début, si elle n'est pas déjà passée.
            if (p.getDateDebut() != null) {
                LocalDate debut = p.getDateDebut().toLocalDate();
                if (!debut.isBefore(today)) {
                    crees += upsert("LANCEMENT:" + p.getId(), "LANCEMENT", p.getId(), debut,
                            "Lancement — " + p.getNom(), messageLancement(p));
                }
            }

            // Rappels avant la fin : J-3, J-1 (uniquement si encore à venir).
            if (p.getDateFin() != null) {
                LocalDate fin = p.getDateFin().toLocalDate();
                for (int j : RAPPELS_JOURS) {
                    LocalDate prevue = fin.minusDays(j);
                    if (prevue.isBefore(today)) { continue; }
                    crees += upsert("RAPPEL_J" + j + ":" + p.getId(), "RAPPEL_J" + j, p.getId(), prevue,
                            "Rappel fin J-" + j + " — " + p.getNom(), messageRappel(p, j));
                }
            }
        }
        return crees;
    }

    private int genererAnnonceMensuelle(LocalDate today) {
        LocalDate premier = today.withDayOfMonth(1);
        List<Promotion> duMois = promosDuMois(premier);
        if (duMois.isEmpty()) { return 0; }

        String mois = premier.getMonth().getDisplayName(TextStyle.FULL, FR);
        String moisCap = mois.substring(0, 1).toUpperCase(FR) + mois.substring(1);
        String cle = "ANNONCE:" + premier.getYear() + "-" + String.format("%02d", premier.getMonthValue());
        // L'annonce part le 1er du mois ; si déjà passé, on la prévoit pour aujourd'hui.
        LocalDate prevue = premier.isBefore(today) ? today : premier;

        return upsert(cle, "ANNONCE_MENSUELLE", null, prevue,
                "Annonce promotions — " + moisCap + " " + premier.getYear(),
                messageMensuel(premier, duMois, prevue));
    }

    /** Promotions (non annulées/archivées) qui chevauchent le mois donné. */
    private List<Promotion> promosDuMois(LocalDate premier) {
        LocalDate finMois = premier.withDayOfMonth(premier.lengthOfMonth());
        List<Promotion> duMois = new ArrayList<>();
        for (Promotion p : promotionService.lister()) {
            String st = p.getStatut();
            if ("ANNULEE".equals(st) || "ARCHIVEE".equals(st)) { continue; }
            LocalDate d = p.getDateDebut() != null ? p.getDateDebut().toLocalDate() : null;
            LocalDate f = p.getDateFin() != null ? p.getDateFin().toLocalDate() : null;
            boolean apresFin = f != null && f.isBefore(premier);
            boolean avantDebut = d != null && d.isAfter(finMois);
            if (!apresFin && !avantDebut) { duMois.add(p); }
        }
        return duMois;
    }

    /**
     * Crée la proposition si sa clé n'existe pas ; si elle existe et est encore
     * PROPOSEE, met à jour date/titre/message (les promos peuvent bouger).
     * Les propositions VALIDEE/REJETEE/EXPIREE ne sont jamais modifiées.
     */
    private int upsert(String cle, String type, Long promotionId, LocalDate datePrevue,
                       String titre, String message) {
        Optional<EnvoiPropose> ex = parCle(cle);
        if (ex.isPresent()) {
            EnvoiPropose e = ex.get();
            if ("PROPOSEE".equals(e.getStatut())) {
                e.setDatePrevue(datePrevue);
                e.setTitre(titre);
                e.setMessage(message);
                em.merge(e);
            }
            return 0;
        }
        EnvoiPropose e = new EnvoiPropose();
        e.setCle(cle);
        e.setType(type);
        e.setPromotionId(promotionId);
        e.setDatePrevue(datePrevue);
        e.setTitre(titre);
        e.setMessage(message);
        e.setStatut("PROPOSEE");
        em.persist(e);
        return 1;
    }

    /** Passe en EXPIREE les propositions non traitées dont la date est dépassée. */
    public int expirerDepassees() {
        return em.createQuery(
                "UPDATE EnvoiPropose e SET e.statut = 'EXPIREE', e.updatedAt = :now " +
                "WHERE e.statut = 'PROPOSEE' AND e.datePrevue < :today")
                .setParameter("now", LocalDateTime.now())
                .setParameter("today", LocalDate.now())
                .executeUpdate();
    }

    /* ====================== Décisions humaines ====================== */

    /**
     * Valide une proposition : crée une campagne en BROUILLON (aucun envoi
     * automatique) et lie la proposition à cette campagne.
     *
     * @param listeId   audience facultative (liste de diffusion) choisie à la validation
     * @param segmentId audience facultative (segment) choisie à la validation
     */
    public EnvoiPropose valider(Long id, Long listeId, Long segmentId, String baseUrl) {
        EnvoiPropose e = em.find(EnvoiPropose.class, id);
        if (e == null) { throw new IllegalArgumentException("Proposition introuvable"); }
        if (!"PROPOSEE".equals(e.getStatut())) {
            throw new ValidationException("statut",
                    "Seule une proposition au statut « PROPOSEE » peut être validée.");
        }

        Promotion p = e.getPromotionId() != null ? em.find(Promotion.class, e.getPromotionId()) : null;

        String corps;
        ModeleMessage modele;
        if (p != null) {
            // Contrôle anti-variable-vide : on bloque la validation tant qu'un
            // élément indispensable au message / à la pièce jointe manque.
            List<String> manquants = elementsManquants(p);
            if (!manquants.isEmpty()) {
                throw new ValidationException("variables",
                        "Validation impossible — éléments manquants : " + String.join(", ", manquants)
                                + ". Complétez la promotion puis réessayez.");
            }
            corps = construireMessage(e, p);
            verifierResidu(corps);
            // Pièce jointe .xlsx (produits de la promo) hébergée puis attachée en en-tête document.
            byte[] xlsx = xlsxService.genererClasseurProduits(p.getId());
            String url = urlMedia(baseUrl, mediaFichierService.enregistrer(
                    xlsx, PromotionXlsxService.MIME, "Promotion-" + slug(p.getNom()) + ".xlsx").getId());
            modele = creerModele(e.getTitre(), corps, "document", url);
        } else if ("ANNONCE_MENSUELLE".equals(e.getType())) {
            LocalDate premier = premierDuMois(e);
            List<Promotion> promos = promosDuMois(premier);
            List<String> manquants = elementsMensuelsManquants(promos);
            if (!manquants.isEmpty()) {
                throw new ValidationException("variables",
                        "Validation impossible — éléments manquants : " + String.join(", ", manquants)
                                + ". Complétez les promotions du mois puis réessayez.");
            }
            corps = messageMensuel(premier, promos, e.getDatePrevue());
            verifierResidu(corps);
            // Pièce jointe .xlsx regroupant les produits de toutes les promos du mois.
            List<Long> ids = new ArrayList<>();
            for (Promotion pr : promos) { ids.add(pr.getId()); }
            byte[] xlsx = xlsxService.genererClasseurPromos(ids);
            String nomFichier = "Promotions-" + premier.getYear() + "-"
                    + String.format("%02d", premier.getMonthValue()) + ".xlsx";
            String url = urlMedia(baseUrl, mediaFichierService.enregistrer(
                    xlsx, PromotionXlsxService.MIME, nomFichier).getId());
            modele = creerModele(e.getTitre(), corps, "document", url);
        } else {
            corps = e.getMessage();
            modele = creerModele(e.getTitre(), corps, null, null);
        }

        Campagne c = new Campagne();
        c.setNom(e.getTitre());
        c.setDescription(corps);
        c.setObjectif("Promotion");
        c.setCategorie("PROMOTION");
        c.setStatut("BROUILLON");
        // Messages riches (texte libre + emojis + pièce jointe) : canal WhatsApp Web
        // par défaut ; l'opérateur peut basculer sur Cloud API avant l'envoi.
        c.setCanal("WEB");
        c.setModeleId(modele.getId());
        // L'envoi est programmé à 9h le jour prévu ; l'opérateur peut l'ajuster.
        c.setDateProgrammee(e.getDatePrevue().atTime(9, 0));
        if (listeId != null) { c.setListeId(listeId); }
        else if (e.getListeId() != null) { c.setListeId(e.getListeId()); }
        if (segmentId != null) { c.setSegmentId(segmentId); }
        else if (e.getSegmentId() != null) { c.setSegmentId(e.getSegmentId()); }
        campagneService.creer(c);

        e.setStatut("VALIDEE");
        e.setCampagneId(c.getId());
        e.setListeId(c.getListeId());
        e.setSegmentId(c.getSegmentId());
        return em.merge(e);
    }

    /** Crée un modèle de message dédié (brouillon) rattaché à la campagne validée. */
    private ModeleMessage creerModele(String titre, String corps, String mediaType, String mediaUrl) {
        ModeleMessage m = new ModeleMessage();
        m.setNom(tronquer(titre, 150));
        m.setTypeModele("PROMOTION");
        m.setCategorie("MARKETING");
        m.setLangue("fr");
        m.setCorps(corps);
        if (mediaType != null) {
            m.setEnteteMediaType(mediaType);
            m.setEnteteMediaUrl(mediaUrl);
        }
        m.setStatutApprobation("BROUILLON");
        m.setActif(true);
        return modeleService.creer(m);
    }

    /** Variables d'une promotion ({{nom_promotion}}, {{taux_ug_max}}, etc.).
     *  {{nom_contact}} n'y figure pas : il est résolu par destinataire à l'envoi. */
    private Map<String, String> variablesPromo(Promotion p) {
        Map<String, String> v = new LinkedHashMap<>();
        String nom = nz(p.getNom());
        v.put("nom_promotion", nom);
        v.put("nom_promo", nom); // alias rétro-compatible
        v.put("date_debut", fdate(p.getDateDebut()));
        v.put("date_fin", fdate(p.getDateFin()));
        v.put("responsable", nz(p.getResponsable()));
        v.put("nombre_produits", String.valueOf(nbProduitsActifs(p.getId())));
        v.put("nb_produits", String.valueOf(nbProduitsActifs(p.getId()))); // alias
        BigDecimal taux = maxTauxUg(p.getId());
        v.put("taux_ug_max", taux == null ? "" : taux.stripTrailingZeros().toPlainString());
        v.put("avantage_ug", avantageUg(taux));
        return v;
    }

    /** Formule d'avantage UG, jamais vide : « jusqu'à X % d'unités gratuites » ou « des unités gratuites ». */
    private String avantageUg(BigDecimal taux) {
        return taux == null ? "des unités gratuites"
                : "jusqu'à " + taux.stripTrailingZeros().toPlainString() + " % d'unités gratuites";
    }

    /** Variables agrégées du mois pour l'annonce mensuelle. */
    private Map<String, String> variablesMensuelles(LocalDate premier, List<Promotion> promos, LocalDate prevue) {
        Map<String, String> v = new LinkedHashMap<>();
        String mois = premier.getMonth().getDisplayName(TextStyle.FULL, FR);
        v.put("mois_promotion", mois.substring(0, 1).toUpperCase(FR) + mois.substring(1));

        LocalDate minD = null, maxF = null;
        int nbProd = 0, joursMin = Integer.MAX_VALUE;
        BigDecimal tauxMax = null;
        java.util.LinkedHashSet<String> fins = new java.util.LinkedHashSet<>();
        for (Promotion p : promos) {
            LocalDate d = p.getDateDebut() != null ? p.getDateDebut().toLocalDate() : null;
            LocalDate f = p.getDateFin() != null ? p.getDateFin().toLocalDate() : null;
            if (d != null && (minD == null || d.isBefore(minD))) { minD = d; }
            if (f != null && (maxF == null || f.isAfter(maxF))) { maxF = f; }
            nbProd += nbProduitsActifs(p.getId());
            BigDecimal t = maxTauxUg(p.getId());
            if (t != null && (tauxMax == null || t.compareTo(tauxMax) > 0)) { tauxMax = t; }
            if (f != null) {
                fins.add(f.format(DF));
                long j = java.time.temporal.ChronoUnit.DAYS.between(prevue, f);
                if (j >= 0 && j < joursMin) { joursMin = (int) j; }
            }
        }
        v.put("date_debut_globale", minD == null ? "" : minD.format(DF));
        v.put("date_fin_globale", maxF == null ? "" : maxF.format(DF));
        v.put("nombre_promotions", String.valueOf(promos.size()));
        v.put("nombre_produits", String.valueOf(nbProd));
        v.put("taux_ug_max", tauxMax == null ? "" : tauxMax.stripTrailingZeros().toPlainString());
        v.put("avantage_ug", avantageUg(tauxMax));
        v.put("dates_fin_promotions", String.join(", ", fins));
        v.put("jours_restants_min", joursMin == Integer.MAX_VALUE ? "" : String.valueOf(joursMin));
        return v;
    }

    /** Éléments indispensables manquants pour une promo (libellés lisibles). */
    private List<String> elementsManquants(Promotion p) {
        List<String> m = new ArrayList<>();
        if (nz(p.getNom()).isEmpty()) { m.add("nom de la promotion"); }
        if (p.getDateDebut() == null) { m.add("date de début"); }
        if (p.getDateFin() == null) { m.add("date de fin"); }
        if (nbProduitsActifs(p.getId()) == 0) { m.add("au moins un produit actif"); }
        return m;
    }

    /** Éléments manquants pour l'annonce mensuelle agrégée. */
    private List<String> elementsMensuelsManquants(List<Promotion> promos) {
        List<String> m = new ArrayList<>();
        if (promos == null || promos.isEmpty()) {
            m.add("au moins une promotion active sur le mois");
            return m;
        }
        int prod = 0; boolean dDeb = false, dFin = false;
        for (Promotion p : promos) {
            prod += nbProduitsActifs(p.getId());
            if (p.getDateDebut() != null) { dDeb = true; }
            if (p.getDateFin() != null) { dFin = true; }
        }
        if (prod == 0) { m.add("au moins un produit actif"); }
        if (!dDeb) { m.add("une date de début"); }
        if (!dFin) { m.add("une date de fin"); }
        return m;
    }

    /** Taux d'UG (%) maximal parmi les produits actifs (null si aucun UG en %). */
    private BigDecimal maxTauxUg(Long promotionId) {
        BigDecimal max = null;
        for (com.ubisenderpro.entity.PromotionProduit pp : promotionProduitService.lister(promotionId)) {
            if (!pp.isActif() || pp.getTauxUg() == null || pp.getTauxUg().signum() <= 0) { continue; }
            if (max == null || pp.getTauxUg().compareTo(max) > 0) { max = pp.getTauxUg(); }
        }
        return max;
    }

    private int nbProduitsActifs(Long promotionId) {
        int n = 0;
        for (com.ubisenderpro.entity.PromotionProduit pp : promotionProduitService.lister(promotionId)) {
            if (pp.isActif()) { n++; }
        }
        return n;
    }

    /** Mois (1er jour) ciblé par une proposition d'annonce, depuis sa clé "ANNONCE:yyyy-MM". */
    private LocalDate premierDuMois(EnvoiPropose e) {
        String cle = e.getCle();
        if (cle != null && cle.startsWith("ANNONCE:")) {
            try {
                String[] ym = cle.substring("ANNONCE:".length()).split("-");
                return LocalDate.of(Integer.parseInt(ym[0]), Integer.parseInt(ym[1]), 1);
            } catch (Exception ignore) { /* repli sur datePrevue */ }
        }
        LocalDate dp = e.getDatePrevue() != null ? e.getDatePrevue() : LocalDate.now();
        return dp.withDayOfMonth(1);
    }

    private void verifierResidu(String corps) {
        String reste = corps == null ? "" : corps.replace("{{nom_contact}}", "");
        if (VARIABLE_RESTANTE.matcher(reste).find()) {
            throw new ValidationException("variables",
                    "Le message contient des variables non remplies. Vérifiez la promotion.");
        }
    }

    private String urlMedia(String baseUrl, Long id) {
        return baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "media/" + id;
    }

    private String fdate(LocalDateTime d) { return d == null ? "" : d.toLocalDate().format(DF); }
    private String nz(String s) { return s == null ? "" : s.trim(); }

    private String tronquer(String s, int max) {
        if (s == null) { return null; }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Nom de fichier sûr à partir du nom de la promotion. */
    private String slug(String s) {
        if (s == null || s.trim().isEmpty()) { return "produits"; }
        String t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return t.isEmpty() ? "produits" : t;
    }

    public EnvoiPropose rejeter(Long id, String motif) {
        EnvoiPropose e = em.find(EnvoiPropose.class, id);
        if (e == null) { throw new IllegalArgumentException("Proposition introuvable"); }
        if (!"PROPOSEE".equals(e.getStatut())) {
            throw new ValidationException("statut",
                    "Seule une proposition au statut « PROPOSEE » peut être rejetée.");
        }
        e.setStatut("REJETEE");
        e.setMotifRejet(motif);
        return em.merge(e);
    }

    /* ====================== Messages suggérés ======================
     * Le texte provient des modèles « promo » éditables en base (clé système),
     * avec repli sur les textes par défaut (PromoTemplates) si supprimés.
     * Le contexte ({{nom_promotion}}, {{date_fin}}, {{avantage_ug}}…) est rempli
     * ici ; {{nom_contact}} est laissé pour être résolu par destinataire à l'envoi. */

    /** Corps du modèle prédéfini (base de données, sinon texte par défaut). */
    private String corpsTemplate(String cleSysteme) {
        if (cleSysteme != null) {
            List<ModeleMessage> l = em.createQuery(
                    "SELECT m FROM ModeleMessage m WHERE m.cleSysteme = :c", ModeleMessage.class)
                    .setParameter("c", cleSysteme).setMaxResults(1).getResultList();
            if (!l.isEmpty() && l.get(0).getCorps() != null) { return l.get(0).getCorps(); }
        }
        return PromoTemplates.CORPS.getOrDefault(cleSysteme, "");
    }

    private String messageLancement(Promotion p) {
        return ModeleService.fusionner(corpsTemplate(PromoTemplates.CLE_LANCEMENT), variablesPromo(p));
    }

    /** Rappel par promo : J-1 -> « dernière chance », sinon (J-3) -> « derniers jours ». */
    private String messageRappel(Promotion p, int joursAvant) {
        Map<String, String> v = variablesPromo(p);
        v.put("jours_restants", String.valueOf(joursAvant));
        String cle = joursAvant <= 1 ? PromoTemplates.CLE_DERNIERE_CHANCE : PromoTemplates.CLE_RAPPEL_FIN;
        return ModeleService.fusionner(corpsTemplate(cle), v);
    }

    private String messageMensuel(LocalDate premier, List<Promotion> promos, LocalDate prevue) {
        return ModeleService.fusionner(corpsTemplate(PromoTemplates.CLE_ANNONCE),
                variablesMensuelles(premier, promos, prevue));
    }

    /** Reconstruit le message d'une proposition liée à une promo (à la validation). */
    private String construireMessage(EnvoiPropose e, Promotion p) {
        String type = e.getType();
        if ("LANCEMENT".equals(type)) { return messageLancement(p); }
        if (type != null && type.startsWith("RAPPEL_J")) { return messageRappel(p, joursRappel(type)); }
        return e.getMessage();
    }

    private int joursRappel(String type) {
        try { return Integer.parseInt(type.substring("RAPPEL_J".length())); }
        catch (Exception ex) { return 1; }
    }
}
