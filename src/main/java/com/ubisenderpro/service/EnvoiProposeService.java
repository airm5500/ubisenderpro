package com.ubisenderpro.service;

import com.ubisenderpro.entity.Campagne;
import com.ubisenderpro.entity.DispoEvenement;
import com.ubisenderpro.entity.DispoProduit;
import com.ubisenderpro.entity.DispoRegle;
import com.ubisenderpro.entity.InfoEvenement;
import com.ubisenderpro.entity.EnvoiPropose;
import com.ubisenderpro.entity.MediaFichier;
import com.ubisenderpro.entity.ModeleMessage;
import com.ubisenderpro.entity.Promotion;

import com.fasterxml.jackson.databind.ObjectMapper;

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
    /** Sérialisation des variables de contexte (figées sur le modèle à la validation). */
    private static final ObjectMapper JSON = new ObjectMapper();

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
    @EJB
    private DispoEvenementService dispoEvenementService;
    @EJB
    private DispoProduitService dispoProduitService;
    @EJB
    private DispoXlsxService dispoXlsxService;
    @EJB
    private ParametreService parametreService;
    @EJB
    private AudienceService audienceService;
    @EJB
    private DispoRegleService dispoRegleService;
    @EJB
    private InfoEvenementService infoEvenementService;

    /** Paramètres techniques (§12, §11 info) : traitement auto sans validation. */
    public static final String CLE_RUPTURE_SANS_AVIS = "rupture.envoi_sans_avis";
    public static final String CLE_INFO_SANS_AVIS = "information.envoi_sans_avis";
    public static final String CLE_ANNIV_SANS_AVIS = "anniversaire.envoi_sans_avis";
    /** URL publique de base (pour joindre les pièces jointes en mode auto, hors contexte HTTP). */
    public static final String CLE_URL_BASE = "app.url_base";
    /** Audience virtuelle : contacts dont c'est l'anniversaire aujourd'hui. */
    public static final String AUDIENCE_ANNIVERSAIRE = "ANNIVERSAIRE_JOUR";

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

        // 3) Événements de disponibilité / rupture actifs avec au moins un produit.
        crees += genererPropositionsDispo(today);
        // 4) Informations clients / alertes opérationnelles actives.
        crees += genererPropositionsInfo(today);
        // 5) Anniversaires du jour (s'il y a des contacts éligibles).
        crees += genererAnniversaires(today);
        return crees;
    }

    /**
     * Génère les propositions puis renvoie la liste des propositions <b>nouvellement
     * créées</b> (pour un aperçu/sélection). L'utilisateur pourra ensuite écarter
     * (rejeter) celles qu'il ne veut pas conserver.
     */
    public Map<String, Object> genererAvecApercu() {
        Long maxAvant = em.createQuery("SELECT COALESCE(MAX(e.id), 0) FROM EnvoiPropose e", Long.class).getSingleResult();
        int crees = genererPropositions();
        int expirees = expirerDepassees();
        List<EnvoiPropose> nouvelles = em.createQuery(
                "SELECT e FROM EnvoiPropose e WHERE e.id > :m AND e.statut = 'PROPOSEE' ORDER BY e.datePrevue, e.id",
                EnvoiPropose.class).setParameter("m", maxAvant).getResultList();
        List<Map<String, Object>> items = new ArrayList<>();
        for (EnvoiPropose e : nouvelles) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("titre", e.getTitre());
            m.put("type", e.getType());
            m.put("source", e.getSource());
            m.put("datePrevue", e.getDatePrevue());
            items.add(m);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("crees", crees);
        res.put("expirees", expirees);
        res.put("items", items);
        return res;
    }

    /** Rejette plusieurs propositions (aperçu : celles non conservées). Renvoie le nombre traité. */
    public int rejeterPlusieurs(List<Long> ids, String motif) {
        if (ids == null) { return 0; }
        int n = 0;
        for (Long id : ids) {
            try { rejeter(id, motif); n++; } catch (RuntimeException ignore) { /* déjà traité */ }
        }
        return n;
    }

    /** Génère une proposition « anniversaires du jour » s'il existe des contacts éligibles. */
    private int genererAnniversaires(LocalDate today) {
        long n = compteAnniversairesEligibles(today.getDayOfMonth(), today.getMonthValue());
        if (n == 0) { return 0; }
        String jj = String.format("%02d/%02d", today.getDayOfMonth(), today.getMonthValue());
        return upsertInfo("ANNIV:" + today, "ANNIVERSAIRE_CLIENT", null, today,
                "Anniversaires du " + jj + " (" + n + ")",
                corpsTemplate(InfoTemplates.clePourType("ANNIVERSAIRE_CLIENT")), AUDIENCE_ANNIVERSAIRE);
    }

    /** Nombre de contacts éligibles aux vœux d'anniversaire pour un jour/mois (§10),
     *  hors contacts ayant déjà reçu leurs vœux cette année (dédup annuel). */
    private long compteAnniversairesEligibles(int jour, int mois) {
        return em.createQuery(
                "SELECT COUNT(ct) FROM ClientContact ct WHERE ct.actif = true AND ct.consentRelationnel = true " +
                "AND ct.desabonne = false AND ct.bloque = false AND ct.jourNaissance = :j AND ct.moisNaissance = :m " +
                "AND ct.numeroWhatsapp IS NOT NULL AND ct.numeroWhatsapp <> '' " +
                "AND NOT EXISTS (SELECT 1 FROM AnniversaireEnvoi a WHERE a.contactId = ct.id AND a.annee = :an)", Long.class)
                .setParameter("j", jour).setParameter("m", mois)
                .setParameter("an", LocalDate.now().getYear()).getSingleResult();
    }

    /** Une proposition par information active (hors anniversaires, générés à part). */
    private int genererPropositionsInfo(LocalDate today) {
        int crees = 0;
        for (InfoEvenement i : infoEvenementService.lister()) {
            String st = i.getStatut();
            if ("ENVOYEE".equals(st) || "ANNULEE".equals(st) || "ARCHIVEE".equals(st) || "EXPIREE".equals(st)) { continue; }
            if ("ANNIVERSAIRE_CLIENT".equals(i.getType())) { continue; } // géré par la génération anniversaires
            LocalDate prevue = i.getDateEnvoi() != null ? i.getDateEnvoi().toLocalDate() : today;
            if (prevue.isBefore(today)) { prevue = today; }
            crees += upsertInfo("INFO:" + i.getId(), i.getType(), i.getId(), prevue,
                    i.getTitre(), messageInfo(i), i.getAudience());
        }
        return crees;
    }

    private int upsertInfo(String cle, String type, Long infoId, LocalDate datePrevue,
                           String titre, String message, String audience) {
        Optional<EnvoiPropose> ex = parCle(cle);
        if (ex.isPresent()) {
            EnvoiPropose e = ex.get();
            if ("PROPOSEE".equals(e.getStatut())) {
                e.setDatePrevue(datePrevue);
                e.setTitre(titre);
                e.setMessage(message);
                e.setAudience(audience);
                em.merge(e);
            }
            return 0;
        }
        EnvoiPropose e = new EnvoiPropose();
        e.setCle(cle);
        e.setType(type);
        e.setSource("INFO");
        e.setInfoId(infoId);
        e.setDatePrevue(datePrevue);
        e.setTitre(titre);
        e.setMessage(message);
        e.setAudience(audience);
        e.setStatut("PROPOSEE");
        em.persist(e);
        return 1;
    }

    /** Propositions issues des événements dispo/rupture actifs comportant des produits.
     *  Le risque de rupture suit les règles de programmation configurables (§11). */
    private int genererPropositionsDispo(LocalDate today) {
        int crees = 0;
        for (DispoEvenement e : dispoEvenementService.lister()) {
            String st = e.getStatut();
            if ("ENVOYEE".equals(st) || "ANNULEE".equals(st) || "ARCHIVEE".equals(st)) { continue; }
            if (nbProduitsDispoActifs(e.getId()) == 0) { continue; }

            if ("RISQUE_RUPTURE".equals(e.getType())) {
                List<DispoRegle> regles = dispoRegleService.listerActives("RISQUE_RUPTURE");
                if (!regles.isEmpty()) {
                    for (DispoRegle r : regles) {
                        LocalDate prevue = prochainJour(r.getJourMois(), today);
                        crees += upsertSource("DISPO:" + e.getId() + ":R" + r.getId(), e.getType(), "DISPO",
                                null, e.getId(), prevue, e.getTitre() + " — " + r.getLibelle(),
                                messageDispo(e), r.getAudience());
                    }
                    continue;
                }
            }

            LocalDate prevue = e.getDateDebut() != null ? e.getDateDebut().toLocalDate() : today;
            if (prevue.isBefore(today)) { prevue = today; }
            crees += upsertSource("DISPO:" + e.getId(), e.getType(), "DISPO", null, e.getId(), prevue,
                    e.getTitre(), messageDispo(e), e.getAudience());
        }
        return crees;
    }

    /** Prochaine date correspondant à un jour du mois (ce mois si à venir, sinon le mois suivant). */
    private LocalDate prochainJour(int jourMois, LocalDate today) {
        int jour = Math.min(jourMois, today.lengthOfMonth());
        LocalDate d = today.withDayOfMonth(jour);
        if (d.isBefore(today)) {
            LocalDate m = today.plusMonths(1);
            d = m.withDayOfMonth(Math.min(jourMois, m.lengthOfMonth()));
        }
        return d;
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
        return upsertSource(cle, type, "PROMO", promotionId, null, datePrevue, titre, message, null);
    }

    private int upsertSource(String cle, String type, String source, Long promotionId, Long evenementId,
                             LocalDate datePrevue, String titre, String message) {
        return upsertSource(cle, type, source, promotionId, evenementId, datePrevue, titre, message, null);
    }

    private int upsertSource(String cle, String type, String source, Long promotionId, Long evenementId,
                             LocalDate datePrevue, String titre, String message, String audience) {
        Optional<EnvoiPropose> ex = parCle(cle);
        if (ex.isPresent()) {
            EnvoiPropose e = ex.get();
            if ("PROPOSEE".equals(e.getStatut())) {
                e.setDatePrevue(datePrevue);
                e.setTitre(titre);
                e.setMessage(message);
                e.setAudience(audience);
                em.merge(e);
            }
            return 0;
        }
        EnvoiPropose e = new EnvoiPropose();
        e.setCle(cle);
        e.setType(type);
        e.setSource(source);
        e.setPromotionId(promotionId);
        e.setEvenementId(evenementId);
        e.setDatePrevue(datePrevue);
        e.setTitre(titre);
        e.setMessage(message);
        e.setAudience(audience);
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

    /* ====================== Traitement automatique (§12) ====================== */

    private boolean flag(String cle) {
        return "true".equalsIgnoreCase(parametreService.valeur(cle, "false"));
    }

    /**
     * Traite automatiquement les propositions dues sans validation humaine, selon
     * les paramètres techniques (rupture / information / anniversaire), si les
     * contrôles métier passent. Crée la campagne (brouillon) prête à l'envoi.
     * Reste sans effet par défaut.
     *
     * @return nombre de propositions transformées en campagne.
     */
    public int traiterPropositionsAuto() {
        boolean dispoAuto = flag(CLE_RUPTURE_SANS_AVIS);
        boolean infoAuto = flag(CLE_INFO_SANS_AVIS);
        boolean annivAuto = flag(CLE_ANNIV_SANS_AVIS);
        if (!dispoAuto && !infoAuto && !annivAuto) { return 0; }
        String baseUrl = parametreService.valeur(CLE_URL_BASE, "");
        int traites = 0;
        List<EnvoiPropose> props = em.createQuery(
                "SELECT e FROM EnvoiPropose e WHERE e.statut = 'PROPOSEE' AND e.datePrevue <= :d " +
                "ORDER BY e.datePrevue", EnvoiPropose.class)
                .setParameter("d", LocalDate.now()).getResultList();
        for (EnvoiPropose e : props) {
            boolean ok = false;
            if ("DISPO".equals(e.getSource())) {
                ok = dispoAuto && baseUrl != null && !baseUrl.trim().isEmpty() && controlesEnvoiAuto(e);
            } else if ("INFO".equals(e.getSource())) {
                boolean anniv = "ANNIVERSAIRE_CLIENT".equals(e.getType()) && e.getInfoId() == null;
                ok = anniv ? annivAuto : (infoAuto && controlesInfoAuto(e));
            }
            if (!ok) { continue; }
            try { valider(e.getId(), null, null, baseUrl); traites++; }
            catch (Exception ignore) { /* proposition ignorée : un contrôle métier a échoué */ }
        }
        return traites;
    }

    /** Contrôles métier bloquant l'envoi auto d'une information. */
    private boolean controlesInfoAuto(EnvoiPropose e) {
        if (e.getInfoId() == null) { return false; }
        InfoEvenement i = em.find(InfoEvenement.class, e.getInfoId());
        if (i == null) { return false; }
        String st = i.getStatut();
        if ("ANNULEE".equals(st) || "ARCHIVEE".equals(st) || "ENVOYEE".equals(st) || "EXPIREE".equals(st)) { return false; }
        if (!elementsInfoManquants(i).isEmpty()) { return false; }
        return !VARIABLE_RESTANTE.matcher(retirerTokensContact(messageInfo(i))).find();
    }

    /** Contrôles métier bloquant l'envoi auto (§12). */
    private boolean controlesEnvoiAuto(EnvoiPropose e) {
        if (e.getEvenementId() == null) { return false; }
        DispoEvenement evt = em.find(DispoEvenement.class, e.getEvenementId());
        if (evt == null) { return false; }
        String st = evt.getStatut();
        if ("ANNULEE".equals(st) || "ARCHIVEE".equals(st) || "ENVOYEE".equals(st)) { return false; }
        if (nbProduitsDispoActifs(evt.getId()) == 0) { return false; }          // aucun produit concerné
        String cle = DispoTemplates.clePourTypeEvenement(evt.getType());
        if (cle == null || corpsTemplate(cle).isEmpty()) { return false; }       // modèle invalide
        String corps = messageDispo(evt);
        if (VARIABLE_RESTANTE.matcher(retirerTokensContact(corps)).find()) { return false; } // variables non résolues
        return true;
    }

    private String retirerTokensContact(String corps) {
        String reste = corps == null ? "" : corps;
        for (String t : TOKENS_CONTACT) { reste = reste.replace("{{" + t + "}}", ""); }
        return reste;
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

        // URL publique des pièces jointes : préférer app.url_base (reverse proxy HTTPS) si défini.
        String paramBase = parametreService.valeur(CLE_URL_BASE, "");
        if (paramBase != null && !paramBase.trim().isEmpty()) { baseUrl = paramBase.trim(); }

        Promotion p = e.getPromotionId() != null ? em.find(Promotion.class, e.getPromotionId()) : null;

        String corps;
        ModeleMessage modele;
        String categorie = "PROMOTION";
        String objectif = "Promotion";
        String audienceCampagne = null;
        Long cibleListeId = null;
        Long cibleSegmentationId = null;
        String cibleSegmentationIds = null;   // sélection multiple de segments (CSV)
        String cibleAgence = null;
        String cibleRegion = null;
        String cibleTournee = null;
        String cibleContactIds = null;
        // Variables de contexte figées sur le modèle : permettent de remplir les
        // paramètres d'un template Meta (canal API) avec les valeurs de la campagne.
        Map<String, String> contexte = new LinkedHashMap<>();
        if ("ANNIVERSAIRE_CLIENT".equals(e.getType()) && e.getInfoId() == null) {
            corps = corpsTemplate(InfoTemplates.clePourType("ANNIVERSAIRE_CLIENT"));
            verifierResidu(corps);
            modele = creerModele(e.getTitre(), corps, null, null, "ANNIVERSAIRE_CLIENT", contexte);
            categorie = "INFORMATION";
            objectif = "Anniversaire";
            audienceCampagne = AUDIENCE_ANNIVERSAIRE;
        } else if (e.getInfoId() != null) {
            InfoEvenement info = em.find(InfoEvenement.class, e.getInfoId());
            if (info == null) { throw new ValidationException("info", "Information introuvable."); }
            List<String> manquants = elementsInfoManquants(info);
            if (!manquants.isEmpty()) {
                throw new ValidationException("variables",
                        "Validation impossible — éléments manquants : " + String.join(", ", manquants)
                                + ". Complétez l'information puis réessayez.");
            }
            corps = messageInfo(info);
            verifierResidu(corps);
            contexte = variablesInfo(info);
            modele = creerModele(e.getTitre(), corps, null, null, nz(info.getType()).isEmpty() ? "INFORMATION" : info.getType(), contexte);
            categorie = "INFORMATION";
            objectif = "Information";
            audienceCampagne = e.getAudience() != null ? e.getAudience() : info.getAudience();
            if ("LISTE_DE_DIFFUSION".equals(audienceCampagne)) { cibleListeId = info.getListeId(); }
            else if ("SEGMENTS_SELECTIONNES".equals(audienceCampagne)) {
                if (!nz(info.getSegmentationIds()).isEmpty()) { cibleSegmentationIds = info.getSegmentationIds(); }
                else { cibleSegmentationId = info.getSegmentationId(); }
            }
            else if ("AGENCE".equals(audienceCampagne)) { cibleAgence = info.getAgence(); }
            else if ("REGION".equals(audienceCampagne)) { cibleRegion = info.getRegion(); }
            else if ("TOURNEE".equals(audienceCampagne)) { cibleTournee = info.getTournee(); }
            else if ("CONTACTS_MANUELS".equals(audienceCampagne)) { cibleContactIds = info.getContactIds(); }
        } else if (e.getEvenementId() != null) {
            DispoEvenement evt = em.find(DispoEvenement.class, e.getEvenementId());
            if (evt == null) { throw new ValidationException("evenement", "Événement introuvable."); }
            audienceCampagne = e.getAudience() != null ? e.getAudience() : evt.getAudience();
            // Ciblage porté par l'événement de disponibilité (segments / agence).
            if ("SEGMENTS_SELECTIONNES".equals(audienceCampagne)) {
                if (!nz(evt.getSegmentationIds()).isEmpty()) { cibleSegmentationIds = evt.getSegmentationIds(); }
                else if (evt.getSegmentationId() != null) { cibleSegmentationId = evt.getSegmentationId(); }
            } else if ("AGENCE".equals(audienceCampagne)) {
                cibleAgence = evt.getAgence();
            }
            List<String> manquants = elementsDispoManquants(evt);
            if (!manquants.isEmpty()) {
                throw new ValidationException("variables",
                        "Validation impossible — éléments manquants : " + String.join(", ", manquants)
                                + ". Complétez l'événement puis réessayez.");
            }
            corps = messageDispo(evt);
            verifierResidu(corps);
            contexte = variablesDispo(evt);
            // Bulletin .xlsx des produits de l'événement, attaché en en-tête document.
            byte[] xlsx = dispoXlsxService.genererBulletin(evt.getId());
            String url = urlMedia(baseUrl, mediaFichierService.enregistrer(
                    xlsx, DispoXlsxService.MIME, bulletinNom(evt)).getId());
            modele = creerModele(e.getTitre(), corps, "document", url, nz(evt.getType()).isEmpty() ? "DISPONIBILITE" : evt.getType(), contexte);
            categorie = "DISPONIBILITE";
            objectif = "Disponibilité / Rupture";
        } else if (p != null) {
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
            contexte = variablesContextePromo(e, p);
            // Pièce jointe .xlsx (produits de la promo) hébergée puis attachée en en-tête document.
            byte[] xlsx = xlsxService.genererClasseurProduits(p.getId());
            String url = urlMedia(baseUrl, mediaFichierService.enregistrer(
                    xlsx, PromotionXlsxService.MIME, "Promotion-" + slug(p.getNom()) + ".xlsx").getId());
            modele = creerModele(e.getTitre(), corps, "document", url, "PROMOTION", contexte);
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
            contexte = variablesMensuelles(premier, promos, e.getDatePrevue());
            // Pièce jointe .xlsx regroupant les produits de toutes les promos du mois.
            List<Long> ids = new ArrayList<>();
            for (Promotion pr : promos) { ids.add(pr.getId()); }
            byte[] xlsx = xlsxService.genererClasseurPromos(ids);
            String nomFichier = "Promotions-" + premier.getYear() + "-"
                    + String.format("%02d", premier.getMonthValue()) + ".xlsx";
            String url = urlMedia(baseUrl, mediaFichierService.enregistrer(
                    xlsx, PromotionXlsxService.MIME, nomFichier).getId());
            modele = creerModele(e.getTitre(), corps, "document", url, "PROMOTION", contexte);
        } else {
            corps = e.getMessage();
            modele = creerModele(e.getTitre(), corps, null, null, "PROMOTION", contexte);
        }

        Campagne c = new Campagne();
        c.setNom(e.getTitre());
        c.setDescription(corps);
        c.setObjectif(objectif);
        c.setCategorie(categorie);
        c.setStatut("BROUILLON");
        // Messages riches (texte libre + emojis + pièce jointe) : canal WhatsApp Web
        // par défaut ; l'opérateur peut basculer sur Cloud API avant l'envoi.
        c.setCanal("WEB");
        c.setModeleId(modele.getId());
        // Audience (§16) : mémorise le ciblage et les segmentations résolues.
        if (audienceCampagne != null && !audienceCampagne.isEmpty()) {
            c.setAudience(audienceCampagne);
            c.setSegmentationIds(audienceService.segmentationIdsCsv(audienceCampagne));
        }
        // Ciblage explicite porté par l'information (liste / segmentation(s) / agence / région).
        if (cibleListeId != null) { c.setListeId(cibleListeId); }
        if (cibleSegmentationId != null) { c.setSegmentationId(cibleSegmentationId); }
        if (cibleSegmentationIds != null && !cibleSegmentationIds.isEmpty()) { c.setSegmentationIds(cibleSegmentationIds); }
        if (cibleAgence != null && !cibleAgence.isEmpty()) { c.setAgenceCible(cibleAgence); }
        if (cibleRegion != null && !cibleRegion.isEmpty()) { c.setRegionCible(cibleRegion); }
        if (cibleTournee != null && !cibleTournee.isEmpty()) { c.setTourneeCible(cibleTournee); }
        if (cibleContactIds != null && !cibleContactIds.isEmpty()) { c.setContactIds(cibleContactIds); }
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
        return creerModele(titre, corps, mediaType, mediaUrl, "PROMOTION", null);
    }

    private ModeleMessage creerModele(String titre, String corps, String mediaType, String mediaUrl,
                                      String typeModele, Map<String, String> contexte) {
        ModeleMessage m = new ModeleMessage();
        m.setNom(tronquer(titre, 150));
        m.setTypeModele(typeModele);
        m.setCategorie("MARKETING");
        m.setLangue("fr");
        m.setCorps(corps);
        if (mediaType != null) {
            m.setEnteteMediaType(mediaType);
            m.setEnteteMediaUrl(mediaUrl);
        }
        m.setVariablesContexte(serialiserContexte(contexte));
        m.setStatutApprobation("BROUILLON");
        m.setActif(true);
        return modeleService.creer(m);
    }

    /** Variables de contexte d'une proposition promo (lancement / rappel J-n). */
    private Map<String, String> variablesContextePromo(EnvoiPropose e, Promotion p) {
        Map<String, String> v = variablesPromo(p);
        String type = e.getType();
        if (type != null && type.startsWith("RAPPEL_J")) {
            v.put("jours_restants", String.valueOf(joursRappel(type)));
        }
        return v;
    }

    /** Sérialise les variables de contexte en JSON (null si vide). */
    private String serialiserContexte(Map<String, String> contexte) {
        if (contexte == null || contexte.isEmpty()) { return null; }
        try {
            return JSON.writeValueAsString(contexte);
        } catch (Exception ex) {
            return null;
        }
    }

    /* ====================== Disponibilités / Ruptures ====================== */

    private int nbProduitsDispoActifs(Long evenementId) {
        int n = 0;
        for (DispoProduit pp : dispoProduitService.lister(evenementId)) { if (pp.isActif()) { n++; } }
        return n;
    }

    private List<String> elementsDispoManquants(DispoEvenement evt) {
        List<String> m = new ArrayList<>();
        if (nz(evt.getTitre()).isEmpty()) { m.add("titre de l'événement"); }
        if (nbProduitsDispoActifs(evt.getId()) == 0) { m.add("au moins un produit actif"); }
        return m;
    }

    /** Message d'un événement dispo : texte du modèle (par type) + variables de contexte,
     *  lignes à variable facultative vide supprimées ; {{nom_contact}}/{{segmentation}} conservés. */
    private String messageDispo(DispoEvenement evt) {
        String cle = DispoTemplates.clePourTypeEvenement(evt.getType());
        String corps = corpsTemplate(cle);
        Map<String, String> v = variablesDispo(evt);
        corps = retirerLignesVides(corps, v);
        return ModeleService.fusionner(corps, v);
    }

    private Map<String, String> variablesDispo(DispoEvenement evt) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("societe", nz(evt.getSociete()));
        v.put("agence", nz(evt.getAgence()));
        v.put("liste_produits", listeProduits(evt.getId()));
        v.put("nombre_produits", String.valueOf(nbProduitsDispoActifs(evt.getId())));
        v.put("date_bulletin", LocalDate.now().format(DF));
        v.put("date_retour", fdate(evt.getDateFin()));
        v.put("date_limite_reservation", fdate(evt.getDateFin()));
        v.put("lien_reservation", premierLienReservation(evt.getId()));
        return v;
    }

    /** Liste lisible des produits d'un événement (nom + péremption). */
    private String listeProduits(Long evenementId) {
        StringBuilder sb = new StringBuilder();
        for (DispoProduit p : dispoProduitService.lister(evenementId)) {
            if (!p.isActif()) { continue; }
            if (sb.length() > 0) { sb.append('\n'); }
            sb.append("✅ ").append(nz(p.getNomProduit()).isEmpty() ? nz(p.getCip7()) : p.getNomProduit());
            if (p.getDatePeremption() != null) {
                sb.append("\n   Péremption : ").append(p.getDatePeremption()
                        .format(java.time.format.DateTimeFormatter.ofPattern("MM/yyyy")));
            }
        }
        return sb.toString();
    }

    private String premierLienReservation(Long evenementId) {
        for (DispoProduit p : dispoProduitService.lister(evenementId)) {
            if (p.isActif() && p.getLienReservation() != null && !p.getLienReservation().trim().isEmpty()) {
                return p.getLienReservation().trim();
            }
        }
        return "";
    }

    private String bulletinNom(DispoEvenement evt) {
        String j = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "Bulletin_" + slug(nz(evt.getType()).isEmpty() ? "Dispo" : evt.getType()) + "_" + j + ".xlsx";
    }

    /* ====================== Informations clients ====================== */

    private List<String> elementsInfoManquants(InfoEvenement i) {
        List<String> m = new ArrayList<>();
        if (nz(i.getTitre()).isEmpty()) { m.add("titre"); }
        String t = i.getType();
        boolean libre = "INFORMATION_GENERALE".equals(t) || "ALERTE_URGENTE".equals(t)
                || "MODIFICATION_HORAIRES".equals(t) || "FERMETURE_AGENCE".equals(t);
        if (libre && nz(i.getMessage()).isEmpty()) { m.add("le message"); }
        return m;
    }

    /** Message d'une information : modèle (par type) + variables de contexte ; lignes
     *  à variable facultative vide supprimées ; tokens contact conservés pour l'envoi. */
    private String messageInfo(InfoEvenement i) {
        String corps = corpsTemplate(InfoTemplates.clePourType(i.getType()));
        if (corps == null || corps.isEmpty()) { corps = nz(i.getMessage()); }
        Map<String, String> v = variablesInfo(i);
        corps = retirerLignesVides(corps, v);
        return ModeleService.fusionner(corps, v);
    }

    private Map<String, String> variablesInfo(InfoEvenement i) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("agence", nz(i.getAgence()));
        v.put("tournee", nz(i.getTournee()));
        v.put("agence_ou_tournee", !nz(i.getTournee()).isEmpty() ? i.getTournee() : nz(i.getAgence()));
        v.put("jour_ferie", nz(i.getJourFerie()));
        v.put("heure_limite_commande", nz(i.getHeureLimiteCommande()));
        v.put("consignes_livraison", nz(i.getConsignesLivraison()));
        v.put("pharmacien_garde", nz(i.getPharmacienGarde()));
        v.put("telephone_pharmacien", nz(i.getTelephonePharmacien()));
        v.put("nouvelle_estimation_livraison",
                nz(i.getNouvelleHeure()).isEmpty() ? "" : "Nouvelle estimation de livraison : " + i.getNouvelleHeure());
        v.put("direction_signataire", !nz(i.getResponsable()).isEmpty() ? i.getResponsable()
                : ("Direction " + (nz(i.getAgence()).isEmpty() ? "Commerciale" : i.getAgence())));
        v.put("message", nz(i.getMessage()));
        return v;
    }

    /** Supprime les lignes contenant une variable de contexte vide (spec §14). */
    private String retirerLignesVides(String corps, Map<String, String> vars) {
        if (corps == null) { return ""; }
        StringBuilder sb = new StringBuilder();
        for (String ligne : corps.split("\n", -1)) {
            boolean drop = false;
            for (Map.Entry<String, String> e : vars.entrySet()) {
                if ((e.getValue() == null || e.getValue().isEmpty()) && ligne.contains("{{" + e.getKey() + "}}")) {
                    drop = true; break;
                }
            }
            if (!drop) { sb.append(ligne).append('\n'); }
        }
        return sb.toString().replaceAll("\n{3,}", "\n\n").trim();
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

    /** Variables résolues par destinataire à l'envoi (à ignorer dans le contrôle anti-résidu). */
    private static final String[] TOKENS_CONTACT = {
        "nom_contact", "civilite", "civilite_complete", "segmentation", "nom_compte", "societe_client",
        "societe", "ville", "region", "email", "telephone"
    };

    private void verifierResidu(String corps) {
        String reste = corps == null ? "" : corps;
        for (String t : TOKENS_CONTACT) { reste = reste.replace("{{" + t + "}}", ""); }
        if (VARIABLE_RESTANTE.matcher(reste).find()) {
            throw new ValidationException("variables",
                    "Le message contient des variables non remplies. Vérifiez la source (promotion / événement).");
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
        String def = PromoTemplates.CORPS.get(cleSysteme);
        if (def == null) { def = DispoTemplates.CORPS.get(cleSysteme); }
        if (def == null) { def = InfoTemplates.CORPS.get(cleSysteme); }
        return def == null ? "" : def;
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
