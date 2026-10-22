package ci.ubipharm.crm.rest.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.ArchivageService;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.PromotionService;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.PromotionCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.PromotionDTO;
import ci.ubipharm.crm.rest.service.dto.PromotionDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.PromotionProduitDTO;
import ci.ubipharm.crm.rest.service.dto.PromotionProduitDemandeDTO;
import ci.ubipharm.crm.rest.service.impl.AvantagePromotion.Avantage;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

/**
 * Promotions : brouillon, puis active (proposee aux campagnes), terminee d'office une fois sa periode depassee,
 * archivee a la main. Une promotion terminee dont on prolonge la fin redevient active.
 */
@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    private static final DateTimeFormatter DATE_LISIBLE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String SELECT = """
            select p.id as id, p.code as code, p.nom as nom, p.description as description, p.date_debut as dateDebut, p.date_fin as dateFin,
                   p.statut as statut, c.nom_complet as creePar, p.created_at as creeLe, m.nom_complet as modifiePar, p.updated_at as modifieLe,
                   (select count(*) from crm_promotion_produit pp where pp.promotion_id = p.id and pp.actif = true) as nbProduits
            from crm_promotion p
            join crm_utilisateur c on c.id = p.cree_par
            left join crm_utilisateur m on m.id = p.modifie_par
            """;

    private static final String SELECT_PRODUITS = """
            select pp.id as id, pp.produit_id as produitId, pr.code as produitCode, pr.libelle as produitLibelle, pr.statut as produitStatut,
                   pr.conditionnement as conditionnement, pr.prix as prixPublic, pp.quantite_min as quantiteMin, pp.avantage as avantage,
                   pp.ug_quantite as ugQuantite, pp.ug_pourcentage as ugPourcentage, pp.ug_plafond as ugPlafond, pp.prix_promo as prixPromo,
                   pp.actif as actif, pp.import_id as importId, coalesce(pp.updated_at, pp.created_at) as modifieLe
            from crm_promotion_produit pp
            join crm_produit pr on pr.id = pp.produit_id
            """;

    private final EntityManager em;
    private final PerimetreService perimetreService;
    private final JournalActionService journalActionService;
    private final ArchivageService archivageService;

    @Override
    @Transactional(readOnly = true)
    public List<PromotionDTO> lister(PromotionCriteresDTO criteres) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> parametres = new LinkedHashMap<>();
        switch (criteres.getStatut()) {
            case "TOUS" -> { }
            case "EN_COURS" -> where.append(" and p.statut in ('BROUILLON', 'PROGRAMMEE', 'ACTIVE', 'SUSPENDUE')");
            default -> {
                where.append(" and p.statut = :statut");
                parametres.put("statut", criteres.getStatut());
            }
        }
        if (criteres.getQ() != null && !criteres.getQ().isBlank()) {
            where.append(" and (p.code like :q or p.nom like :q)");
            parametres.put("q", "%" + criteres.getQ().trim() + "%");
        }
        Query requete = em.createNativeQuery(SELECT + where
                + " order by field(p.statut, 'ACTIVE', 'PROGRAMMEE', 'BROUILLON', 'SUSPENDUE', 'TERMINEE', 'ARCHIVEE'), p.date_debut desc, p.id desc", Tuple.class);
        parametres.forEach(requete::setParameter);
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionDTO consulter(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where p.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Promotion introuvable");
        }
        return mapTupleToDto(lignes.getFirst());
    }

    @Override
    @Transactional
    public PromotionDTO creer(PromotionDemandeDTO demande) {
        verifier(null, demande);
        em.createNativeQuery("""
                insert into crm_promotion (code, nom, description, date_debut, date_fin, statut, cree_par, created_at)
                values (:code, :nom, :description, :debut, :fin, 'BROUILLON', :par, utc_timestamp())
                """)
                .setParameter("code", demande.getCode().trim())
                .setParameter("nom", demande.getNom().trim())
                .setParameter("description", vide(demande.getDescription()))
                .setParameter("debut", demande.getDateDebut())
                .setParameter("fin", demande.getDateFin())
                .setParameter("par", utilisateur())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        journalActionService.enregistrer("PROMOTION_CREATION", "crm_promotion", id, demande.getCode().trim() + " " + demande.getNom().trim()
                + " du " + demande.getDateDebut().format(DATE_LISIBLE) + " au " + demande.getDateFin().format(DATE_LISIBLE));
        return consulter(id);
    }

    @Override
    @Transactional
    public PromotionDTO modifier(Long id, PromotionDemandeDTO demande) {
        PromotionDTO avant = verrouiller(id);
        if ("ARCHIVEE".equals(avant.getStatut())) {
            throw new ErreurMetierException("Cette promotion est archivée : réactivez-la pour la modifier.");
        }
        verifier(id, demande);
        // Une promotion publiee suit sa periode : prolongee, elle redevient active ; ecourtee, elle se termine.
        // Brouillon et suspendue gardent leur statut : seule une action de l'utilisateur les publie.
        String statut = "BROUILLON".equals(avant.getStatut()) || "SUSPENDUE".equals(avant.getStatut()) ? avant.getStatut()
                : statutDePeriode(demande.getDateDebut(), demande.getDateFin());
        em.createNativeQuery("""
                update crm_promotion set code = :code, nom = :nom, description = :description, date_debut = :debut, date_fin = :fin,
                       statut = :statut, modifie_par = :par, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("code", demande.getCode().trim())
                .setParameter("nom", demande.getNom().trim())
                .setParameter("description", vide(demande.getDescription()))
                .setParameter("debut", demande.getDateDebut())
                .setParameter("fin", demande.getDateFin())
                .setParameter("statut", statut)
                .setParameter("par", utilisateur())
                .setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("PROMOTION_MODIFICATION", "crm_promotion", id, avant.getCode() + " " + demande.getNom().trim()
                + " du " + demande.getDateDebut().format(DATE_LISIBLE) + " au " + demande.getDateFin().format(DATE_LISIBLE)
                + (statut.equals(avant.getStatut()) ? "" : " : " + avant.getStatut() + " -> " + statut));
        return consulter(id);
    }

    @Override
    @Transactional
    public PromotionDTO activer(Long id) {
        PromotionDTO promotion = verrouiller(id);
        if (!"BROUILLON".equals(promotion.getStatut())) {
            throw new ErreurMetierException("Seule une promotion en brouillon s'active.");
        }
        if (promotion.getNbProduits() == 0) {
            throw new ErreurMetierException("Ajoutez au moins un produit avant d'activer la promotion.");
        }
        if (promotion.getDateFin().isBefore(aujourdhui())) {
            throw new ErreurMetierException("La période de cette promotion est déjà passée : prolongez sa date de fin.");
        }
        // Activee avant son debut, elle est programmee et s'ouvrira d'elle-meme
        return promotion.getDateDebut().isAfter(aujourdhui()) ? changerStatut(promotion, "PROGRAMMEE", "PROMOTION_PROGRAMMATION")
                : changerStatut(promotion, "ACTIVE", "PROMOTION_ACTIVATION");
    }

    @Override
    @Transactional
    public PromotionDTO desactiver(Long id) {
        PromotionDTO promotion = verrouiller(id);
        if (!"ACTIVE".equals(promotion.getStatut()) && !"PROGRAMMEE".equals(promotion.getStatut())) {
            throw new ErreurMetierException("Seule une promotion active ou programmée se désactive.");
        }
        return changerStatut(promotion, "SUSPENDUE", "PROMOTION_DESACTIVATION");
    }

    @Override
    @Transactional
    public PromotionDTO archiver(Long id) {
        PromotionDTO promotion = verrouiller(id);
        if ("ARCHIVEE".equals(promotion.getStatut())) {
            throw new ErreurMetierException("Cette promotion est déjà archivée.");
        }
        return changerStatut(promotion, "ARCHIVEE", "PROMOTION_ARCHIVAGE");
    }

    @Override
    @Transactional
    public PromotionDTO reactiver(Long id) {
        PromotionDTO promotion = verrouiller(id);
        if ("SUSPENDUE".equals(promotion.getStatut())) {
            // Reprise : elle retrouve le statut de sa periode
            return changerStatut(promotion, statutDePeriode(promotion.getDateDebut(), promotion.getDateFin()), "PROMOTION_REACTIVATION");
        }
        if (!"ARCHIVEE".equals(promotion.getStatut())) {
            throw new ErreurMetierException("Seule une promotion archivée ou désactivée se réactive.");
        }
        // Elle repart en brouillon : on la relit avant de la proposer de nouveau aux campagnes
        return changerStatut(promotion, "BROUILLON", "PROMOTION_REACTIVATION");
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionProduitDTO> produits(Long id, boolean avecRetires) {
        consulter(id);
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT_PRODUITS + " where pp.promotion_id = :id" + (avecRetires ? "" : " and pp.actif = true")
                + " order by pp.actif desc, pr.libelle, pr.code", Tuple.class).setParameter("id", id).getResultList();
        return lignes.stream().map(this::mapTupleToProduitDto).toList();
    }

    @Override
    @Transactional
    public PromotionProduitDTO ajouterProduit(Long id, PromotionProduitDemandeDTO demande) {
        PromotionDTO promotion = verrouillerModifiable(id);
        Tuple produit = produit(demande.getProduitId());
        Avantage avantage = avantage(demande, produit);
        @SuppressWarnings("unchecked")
        List<Tuple> existantes = em.createNativeQuery("select id as id, actif as actif from crm_promotion_produit where promotion_id = :p and produit_id = :pr",
                Tuple.class).setParameter("p", id).setParameter("pr", demande.getProduitId()).getResultList();
        Long ligneId;
        if (existantes.isEmpty()) {
            em.createNativeQuery("""
                    insert into crm_promotion_produit (promotion_id, produit_id, quantite_min, avantage, ug_quantite, ug_pourcentage, ug_plafond,
                                                       prix_promo, actif, cree_par, created_at)
                    values (:p, :pr, :qte, :avantage, :ugq, :ugp, :plafond, :prix, true, :par, utc_timestamp())
                    """)
                    .setParameter("p", id).setParameter("pr", demande.getProduitId())
                    .setParameter("qte", avantage.quantiteMin()).setParameter("avantage", avantage.type())
                    .setParameter("ugq", avantage.ugQuantite()).setParameter("ugp", avantage.ugPourcentage())
                    .setParameter("plafond", avantage.ugPlafond()).setParameter("prix", avantage.prixPromo())
                    .setParameter("par", utilisateur())
                    .executeUpdate();
            ligneId = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        } else if (actif(existantes.getFirst().get("actif"))) {
            throw new ErreurMetierException("« " + produit.get("libelle", String.class) + " » est déjà dans cette promotion : modifiez sa ligne.");
        } else {
            // Un produit retire puis remis reprend sa ligne
            ligneId = existantes.getFirst().get("id", Long.class);
            AvantagePromotion.ecrire(em, ligneId, avantage, null, utilisateur());
        }
        journalActionService.enregistrer("PROMOTION_PRODUIT_AJOUT", "crm_promotion", id,
                promotion.getCode() + " : " + produit.get("code", String.class) + " " + AvantagePromotion.resume(avantage, produit.get("prix", BigDecimal.class)));
        return ligne(id, ligneId);
    }

    @Override
    @Transactional
    public PromotionProduitDTO modifierProduit(Long id, Long ligneId, PromotionProduitDemandeDTO demande) {
        PromotionDTO promotion = verrouillerModifiable(id);
        PromotionProduitDTO avant = ligne(id, ligneId);
        if (!avant.isActif()) {
            throw new ErreurMetierException("Ce produit a été retiré de la promotion : ajoutez-le de nouveau.");
        }
        if (!Objects.equals(avant.getProduitId(), demande.getProduitId())) {
            throw new ErreurMetierException("Le produit d'une ligne ne change pas : retirez-la et ajoutez l'autre produit.");
        }
        Tuple produit = produit(demande.getProduitId());
        Avantage avantage = avantage(demande, produit);
        AvantagePromotion.ecrire(em, ligneId, avantage, null, utilisateur());
        journalActionService.enregistrer("PROMOTION_PRODUIT_MODIFICATION", "crm_promotion", id, promotion.getCode() + " : " + avant.getProduitCode()
                + " " + avant.getResume() + " -> " + AvantagePromotion.resume(avantage, produit.get("prix", BigDecimal.class)));
        return ligne(id, ligneId);
    }

    @Override
    @Transactional
    public PromotionProduitDTO retirerProduit(Long id, Long ligneId) {
        PromotionDTO promotion = verrouillerModifiable(id);
        PromotionProduitDTO avant = ligne(id, ligneId);
        if (!avant.isActif()) {
            throw new ErreurMetierException("Ce produit est déjà retiré de la promotion.");
        }
        if (List.of("ACTIVE", "PROGRAMMEE").contains(promotion.getStatut()) && promotion.getNbProduits() == 1) {
            throw new ErreurMetierException("Une promotion publiée garde au moins un produit : désactivez-la ou archivez-la plutôt.");
        }
        em.createNativeQuery("update crm_promotion_produit set actif = false, modifie_par = :par, updated_at = utc_timestamp() where id = :id")
                .setParameter("par", utilisateur()).setParameter("id", ligneId).executeUpdate();
        journalActionService.enregistrer("PROMOTION_PRODUIT_RETRAIT", "crm_promotion", id, promotion.getCode() + " : " + avant.getProduitCode());
        return ligne(id, ligneId);
    }

    @Override
    @Transactional(readOnly = true)
    public FichierDTO exporterProduits(Long id) {
        PromotionDTO promotion = consulter(id);
        List<List<String>> lignes = new ArrayList<>();
        // Les colonnes de l'import, plus le libelle et l'avantage en clair que l'assistant ignore a la reimportation
        lignes.add(List.of("Code promotion", "Code produit", "Libellé produit", "Quantité minimale", "Unités offertes", "% d'unités gratuites",
                "Plafond d'unités gratuites", "Prix promotionnel", "Avantage"));
        for (PromotionProduitDTO p : produits(id, false)) {
            lignes.add(List.of(promotion.getCode(), p.getProduitCode(), p.getProduitLibelle(), String.valueOf(p.getQuantiteMin()),
                    texte(p.getUgQuantite()), texte(p.getUgPourcentage()), texte(p.getUgPlafond()), texte(p.getPrixPromo()), p.getResume()));
        }
        FichierDTO fichier = FichierCsv.csv("promotion_" + promotion.getCode().replaceAll("[^A-Za-z0-9_-]", "_") + ".csv", lignes);
        archivageService.archiver("exports-promotions", fichier.getNom(), fichier.getContenu());
        return fichier;
    }

    @Override
    @Transactional(readOnly = true)
    public void verifierCitables(List<Long> ids) {
        for (Long promotionId : ids) {
            @SuppressWarnings("unchecked")
            List<Tuple> lignes = em.createNativeQuery("select nom as nom, statut as statut, date_fin as dateFin from crm_promotion where id = :id",
                    Tuple.class).setParameter("id", promotionId).getResultList();
            if (lignes.isEmpty()) {
                throw new ErreurMetierException("Promotion introuvable.");
            }
            Tuple t = lignes.getFirst();
            if (!"ACTIVE".equals(t.get("statut", String.class)) || t.get("dateFin", LocalDate.class).isBefore(aujourdhui())) {
                throw new ErreurMetierException("La promotion « " + t.get("nom", String.class) + " » n'est pas en cours : choisissez une promotion active.");
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> valeursCampagne(List<Long> ids) {
        Map<String, String> valeurs = new HashMap<>();
        List<PromotionDTO> promotions = ids.stream().map(this::consulter).toList();
        valeurs.put("calcul.promotion_nom", promotions.stream().map(PromotionDTO::getNom).collect(Collectors.joining(", ")));
        valeurs.put("calcul.promotion_code", promotions.stream().map(PromotionDTO::getCode).collect(Collectors.joining(", ")));
        valeurs.put("calcul.promotion_debut", promotions.stream().map(PromotionDTO::getDateDebut).min(LocalDate::compareTo)
                .map(d -> d.format(DATE_LISIBLE)).orElse(""));
        valeurs.put("calcul.promotion_fin", promotions.stream().map(PromotionDTO::getDateFin).max(LocalDate::compareTo)
                .map(d -> d.format(DATE_LISIBLE)).orElse(""));
        // Un produit cite par deux promotions n'apparait qu'une fois, avec l'avantage de la premiere
        Map<Long, String> lignes = new LinkedHashMap<>();
        for (PromotionDTO promotion : promotions) {
            produits(promotion.getId(), false).stream().filter(p -> "ACTIF".equals(p.getProduitStatut()))
                    .forEach(p -> lignes.putIfAbsent(p.getProduitId(), "• " + p.getProduitLibelle() + " : " + p.getResume()));
        }
        valeurs.put("calcul.produits_promotion", String.join("\n", lignes.values()));
        return valeurs;
    }

    @Override
    @Transactional(readOnly = true)
    public FichierDTO classeurCampagne(List<Long> promotionIds, List<Long> produitIds) {
        try (XSSFWorkbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            CellStyle gras = classeur.createCellStyle();
            Font police = classeur.createFont();
            police.setBold(true);
            gras.setFont(police);
            if (!promotionIds.isEmpty()) {
                feuillePromotions(classeur, gras, promotionIds);
            }
            if (!produitIds.isEmpty()) {
                feuilleDisponibles(classeur, gras, produitIds);
            }
            classeur.write(sortie);
            return FichierDTO.builder().nom(promotionIds.isEmpty() ? "produits_disponibles.xlsx" : "produits_en_promotion.xlsx")
                    .typeContenu("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").contenu(sortie.toByteArray()).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void feuillePromotions(XSSFWorkbook classeur, CellStyle gras, List<Long> ids) {
        Sheet feuille = classeur.createSheet("Produits en promotion");
        String[] entetes = {"Promotion", "Valable jusqu'au", "Code produit", "Produit", "Conditionnement", "Prix public (F)",
                "Quantité minimale", "Avantage", "Prix promotionnel (F)"};
        entetes(feuille, gras, entetes);
        int rang = 1;
        for (Long id : ids) {
            PromotionDTO promotion = consulter(id);
            for (PromotionProduitDTO p : produits(id, false)) {
                if (!"ACTIF".equals(p.getProduitStatut())) {
                    continue;
                }
                Row ligne = feuille.createRow(rang++);
                ligne.createCell(0).setCellValue(promotion.getNom());
                ligne.createCell(1).setCellValue(promotion.getDateFin().format(DATE_LISIBLE));
                ligne.createCell(2).setCellValue(p.getProduitCode());
                ligne.createCell(3).setCellValue(p.getProduitLibelle());
                ligne.createCell(4).setCellValue(Objects.toString(p.getConditionnement(), ""));
                if (p.getPrixPublic() != null) {
                    ligne.createCell(5).setCellValue(p.getPrixPublic().doubleValue());
                }
                ligne.createCell(6).setCellValue(p.getQuantiteMin());
                ligne.createCell(7).setCellValue(p.getResume());
                if (p.getPrixPromo() != null) {
                    ligne.createCell(8).setCellValue(p.getPrixPromo().doubleValue());
                }
            }
        }
        largeurs(feuille, 28, 16, 14, 40, 18, 14, 16, 45, 20);
    }

    private void feuilleDisponibles(XSSFWorkbook classeur, CellStyle gras, List<Long> ids) {
        Sheet feuille = classeur.createSheet("Produits disponibles");
        entetes(feuille, gras, new String[] {"Code produit", "Produit", "Laboratoire", "Conditionnement", "Prix public (F)", "De retour depuis le"});
        @SuppressWarnings("unchecked")
        List<Tuple> produits = em.createNativeQuery("""
                select code as code, libelle as libelle, laboratoire as laboratoire, conditionnement as conditionnement, prix as prix,
                       disponibilite_le as disponibiliteLe
                from crm_produit where id in (:ids) and statut = 'ACTIF' and disponibilite = 'DISPONIBLE' order by libelle
                """, Tuple.class).setParameter("ids", ids).getResultList();
        int rang = 1;
        for (Tuple p : produits) {
            Row ligne = feuille.createRow(rang++);
            ligne.createCell(0).setCellValue(p.get("code", String.class));
            ligne.createCell(1).setCellValue(p.get("libelle", String.class));
            ligne.createCell(2).setCellValue(Objects.toString(p.get("laboratoire"), ""));
            ligne.createCell(3).setCellValue(Objects.toString(p.get("conditionnement"), ""));
            if (p.get("prix") != null) {
                ligne.createCell(4).setCellValue(((Number) p.get("prix")).doubleValue());
            }
            if (p.get("disponibiliteLe") != null) {
                ligne.createCell(5).setCellValue(p.get("disponibiliteLe", java.time.LocalDateTime.class).toLocalDate().format(DATE_LISIBLE));
            }
        }
        largeurs(feuille, 14, 40, 22, 18, 14, 18);
    }

    private static void entetes(Sheet feuille, CellStyle gras, String[] entetes) {
        Row entete = feuille.createRow(0);
        for (int i = 0; i < entetes.length; i++) {
            Cell cellule = entete.createCell(i);
            cellule.setCellValue(entetes[i]);
            cellule.setCellStyle(gras);
        }
        feuille.createFreezePane(0, 1);
    }

    /** Largeurs fixes : autoSizeColumn exige des polices installees sur le serveur. */
    private static void largeurs(Sheet feuille, int... largeurs) {
        for (int i = 0; i < largeurs.length; i++) {
            feuille.setColumnWidth(i, largeurs[i] * 256);
        }
    }

    @Override
    @Transactional
    public int suivrePeriodes() {
        @SuppressWarnings("unchecked")
        List<Tuple> ouvertes = em.createNativeQuery("""
                select id as id, code as code from crm_promotion where statut = 'PROGRAMMEE' and date_debut <= :jour and date_fin >= :jour for update
                """, Tuple.class).setParameter("jour", aujourdhui()).getResultList();
        for (Tuple t : ouvertes) {
            em.createNativeQuery("update crm_promotion set statut = 'ACTIVE', updated_at = utc_timestamp() where id = :id")
                    .setParameter("id", t.get("id", Long.class)).executeUpdate();
            journalActionService.enregistrerSysteme("PROMOTION_DEBUT", "crm_promotion", t.get("id", Long.class),
                    t.get("code", String.class) + " : début de la période programmée");
        }
        @SuppressWarnings("unchecked")
        List<Tuple> echues = em.createNativeQuery("select id as id, code as code from crm_promotion where statut in ('ACTIVE', 'PROGRAMMEE') and date_fin < :jour for update",
                Tuple.class).setParameter("jour", aujourdhui()).getResultList();
        for (Tuple t : echues) {
            em.createNativeQuery("update crm_promotion set statut = 'TERMINEE', updated_at = utc_timestamp() where id = :id")
                    .setParameter("id", t.get("id", Long.class)).executeUpdate();
            journalActionService.enregistrerSysteme("PROMOTION_FIN", "crm_promotion", t.get("id", Long.class),
                    t.get("code", String.class) + " : période terminée");
        }
        return ouvertes.size() + echues.size();
    }

    static String statutDePeriode(LocalDate debut, LocalDate fin) {
        return fin.isBefore(aujourdhui()) ? "TERMINEE" : debut.isAfter(aujourdhui()) ? "PROGRAMMEE" : "ACTIVE";
    }

    private Avantage avantage(PromotionProduitDemandeDTO demande, Tuple produit) {
        Avantage avantage = Avantage.de(demande.getAvantage(), demande.getQuantiteMin() == null ? 1 : demande.getQuantiteMin(), demande.getUgQuantite(), demande.getUgPourcentage(),
                demande.getUgPlafond(), demande.getPrixPromo());
        List<String> motifs = AvantagePromotion.controler(avantage, produit.get("prix", BigDecimal.class));
        if (!motifs.isEmpty()) {
            throw new ErreurMetierException(String.join(" ", motifs));
        }
        return avantage;
    }

    private Tuple produit(Long produitId) {
        @SuppressWarnings("unchecked")
        List<Tuple> produits = em.createNativeQuery("select code as code, libelle as libelle, prix as prix, statut as statut from crm_produit where id = :id",
                Tuple.class).setParameter("id", produitId).getResultList();
        if (produits.isEmpty()) {
            throw new ErreurMetierException("Produit introuvable dans le catalogue.");
        }
        if (!"ACTIF".equals(produits.getFirst().get("statut", String.class))) {
            throw new ErreurMetierException("Le produit « " + produits.getFirst().get("libelle", String.class) + " » est archivé dans le catalogue.");
        }
        return produits.getFirst();
    }

    private PromotionProduitDTO ligne(Long promotionId, Long ligneId) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT_PRODUITS + " where pp.id = :id and pp.promotion_id = :p", Tuple.class)
                .setParameter("id", ligneId).setParameter("p", promotionId).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Produit de la promotion introuvable");
        }
        return mapTupleToProduitDto(lignes.getFirst());
    }

    private PromotionDTO verrouiller(Long id) {
        em.createNativeQuery("select id from crm_promotion where id = :id for update").setParameter("id", id).getResultList();
        return consulter(id);
    }

    private PromotionDTO verrouillerModifiable(Long id) {
        PromotionDTO promotion = verrouiller(id);
        if ("ARCHIVEE".equals(promotion.getStatut())) {
            throw new ErreurMetierException("Cette promotion est archivée : réactivez-la pour changer ses produits.");
        }
        return promotion;
    }

    private void verifier(Long id, PromotionDemandeDTO demande) {
        if (demande.getDateFin().isBefore(demande.getDateDebut())) {
            throw new ErreurMetierException("La date de fin précède la date de début.");
        }
        @SuppressWarnings("unchecked")
        List<String> doublons = em.createNativeQuery("select nom from crm_promotion where code = :code and id <> :id")
                .setParameter("code", demande.getCode().trim()).setParameter("id", id == null ? 0L : id).getResultList();
        if (!doublons.isEmpty()) {
            throw new ErreurMetierException("Le code " + demande.getCode().trim() + " est déjà utilisé par la promotion « " + doublons.getFirst() + " ».");
        }
    }

    private PromotionDTO changerStatut(PromotionDTO promotion, String statut, String action) {
        em.createNativeQuery("update crm_promotion set statut = :statut, modifie_par = :par, updated_at = utc_timestamp() where id = :id")
                .setParameter("statut", statut).setParameter("par", utilisateur()).setParameter("id", promotion.getId()).executeUpdate();
        journalActionService.enregistrer(action, "crm_promotion", promotion.getId(), promotion.getCode() + " " + promotion.getNom());
        return consulter(promotion.getId());
    }

    private Long utilisateur() {
        return perimetreService.perimetreCourant().getUtilisateurId();
    }

    static LocalDate aujourdhui() {
        // Abidjan vit en UTC toute l'annee
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static boolean actif(Object valeur) {
        return valeur instanceof Boolean b ? b : valeur instanceof Number n && n.intValue() != 0;
    }

    private static String vide(String texte) {
        return texte == null || texte.isBlank() ? null : texte.trim();
    }

    private static String texte(Object valeur) {
        if (valeur == null) {
            return "";
        }
        return valeur instanceof BigDecimal b ? b.stripTrailingZeros().toPlainString() : String.valueOf(valeur);
    }

    PromotionDTO mapTupleToDto(Tuple tuple) {
        return PromotionDTO.builder()
                .id(tuple.get("id", Long.class))
                .code(tuple.get("code", String.class))
                .nom(tuple.get("nom", String.class))
                .description(tuple.get("description", String.class))
                .dateDebut(tuple.get("dateDebut", LocalDate.class))
                .dateFin(tuple.get("dateFin", LocalDate.class))
                .statut(tuple.get("statut", String.class))
                .nbProduits(((Number) tuple.get("nbProduits")).longValue())
                .creePar(tuple.get("creePar", String.class))
                .creeLe(tuple.get("creeLe", LocalDateTime.class))
                .modifiePar(tuple.get("modifiePar", String.class))
                .modifieLe(tuple.get("modifieLe", LocalDateTime.class))
                .build();
    }

    PromotionProduitDTO mapTupleToProduitDto(Tuple tuple) {
        BigDecimal prixPublic = tuple.get("prixPublic", BigDecimal.class);
        Avantage avantage = Avantage.de(tuple.get("avantage", String.class), ((Number) tuple.get("quantiteMin")).intValue(),
                tuple.get("ugQuantite", Integer.class), tuple.get("ugPourcentage", BigDecimal.class), tuple.get("ugPlafond", Integer.class),
                tuple.get("prixPromo", BigDecimal.class));
        return PromotionProduitDTO.builder()
                .id(tuple.get("id", Long.class))
                .produitId(tuple.get("produitId", Long.class))
                .produitCode(tuple.get("produitCode", String.class))
                .produitLibelle(tuple.get("produitLibelle", String.class))
                .produitStatut(tuple.get("produitStatut", String.class))
                .conditionnement(tuple.get("conditionnement", String.class))
                .prixPublic(prixPublic)
                .quantiteMin(avantage.quantiteMin())
                .avantage(avantage.type())
                .ugQuantite(avantage.ugQuantite())
                .ugPourcentage(avantage.ugPourcentage())
                .ugPlafond(avantage.ugPlafond())
                .prixPromo(avantage.prixPromo())
                .resume(AvantagePromotion.resume(avantage, prixPublic))
                .actif(actif(tuple.get("actif")))
                .importe(tuple.get("importId") != null)
                .modifieLe(tuple.get("modifieLe", LocalDateTime.class))
                .build();
    }
}
