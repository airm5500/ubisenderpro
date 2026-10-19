package ci.ubipharm.crm.rest.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.ProduitService;
import ci.ubipharm.crm.rest.service.dto.FiltresCatalogueDTO;
import ci.ubipharm.crm.rest.service.dto.ProduitCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.ProduitDTO;
import ci.ubipharm.crm.rest.service.dto.ProduitDemandeDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProduitServiceImpl implements ProduitService {

    private static final String SELECT = """
            select p.id as id, p.code as code, p.libelle as libelle, p.categorie as categorie, p.laboratoire as laboratoire,
                   p.conditionnement as conditionnement, p.prix as prix, p.description as description, p.statut as statut,
                   p.disponibilite as disponibilite, p.disponibilite_le as disponibiliteLe,
                   p.import_id as importId, c.nom_complet as creePar, p.created_at as creeLe, m.nom_complet as modifiePar,
                   p.updated_at as modifieLe
            from crm_produit p
            join crm_utilisateur c on c.id = p.cree_par
            left join crm_utilisateur m on m.id = p.modifie_par
            """;

    private final EntityManager em;
    private final PerimetreService perimetreService;
    private final JournalActionService journalActionService;
    private final ParametreService parametreService;

    @Override
    @Transactional(readOnly = true)
    public PageDTO<ProduitDTO> rechercher(ProduitCriteresDTO criteres) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> parametres = new LinkedHashMap<>();
        if (!"TOUS".equals(criteres.getStatut())) {
            where.append(" and p.statut = :statut");
            parametres.put("statut", criteres.getStatut());
        }
        if (vide(criteres.getQ()) != null) {
            where.append(" and (p.code like :q or p.libelle like :q or p.laboratoire like :q)");
            parametres.put("q", "%" + criteres.getQ().trim() + "%");
        }
        if (vide(criteres.getCategorie()) != null) {
            where.append(" and p.categorie = :categorie");
            parametres.put("categorie", criteres.getCategorie().trim());
        }
        if (criteres.getDisponibilite() != null) {
            where.append("RUPTURE".equals(criteres.getDisponibilite()) ? " and p.disponibilite = 'RUPTURE'" : " and p.disponibilite = 'DISPONIBLE'");
            if ("RETOUR".equals(criteres.getDisponibilite())) {
                where.append(" and p.disponibilite_le >= :retourDepuis");
                parametres.put("retourDepuis", retourDepuis());
            }
        }
        if (vide(criteres.getLaboratoire()) != null) {
            where.append(" and p.laboratoire = :laboratoire");
            parametres.put("laboratoire", criteres.getLaboratoire().trim());
        }

        Query comptage = em.createNativeQuery("select count(*) from crm_produit p" + where);
        parametres.forEach(comptage::setParameter);
        long total = ((Number) comptage.getSingleResult()).longValue();

        Query requete = em.createNativeQuery(SELECT + where + " order by p.statut, p.libelle, p.id", Tuple.class);
        parametres.forEach(requete::setParameter);
        requete.setFirstResult(criteres.getPage() * criteres.getTaille());
        requete.setMaxResults(criteres.getTaille());
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return PageDTO.<ProduitDTO>builder()
                .elements(lignes.stream().map(this::mapTupleToDto).toList())
                .total(total)
                .page(criteres.getPage())
                .taille(criteres.getTaille())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ProduitDTO consulter(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where p.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Produit introuvable");
        }
        return mapTupleToDto(lignes.getFirst());
    }

    @Override
    @Transactional(readOnly = true)
    public FiltresCatalogueDTO filtres() {
        return FiltresCatalogueDTO.builder()
                .categories(valeursDistinctes("categorie"))
                .laboratoires(valeursDistinctes("laboratoire"))
                .build();
    }

    @Override
    @Transactional
    public ProduitDTO creer(ProduitDemandeDTO demande) {
        verifierCode(null, demande.getCode());
        em.createNativeQuery("""
                insert into crm_produit (code, libelle, categorie, laboratoire, conditionnement, prix, description, statut, cree_par, created_at)
                values (:code, :libelle, :categorie, :laboratoire, :conditionnement, :prix, :description, 'ACTIF', :par, utc_timestamp())
                """)
                .setParameter("code", demande.getCode().trim())
                .setParameter("libelle", demande.getLibelle().trim())
                .setParameter("categorie", vide(demande.getCategorie()))
                .setParameter("laboratoire", vide(demande.getLaboratoire()))
                .setParameter("conditionnement", vide(demande.getConditionnement()))
                .setParameter("prix", demande.getPrix())
                .setParameter("description", vide(demande.getDescription()))
                .setParameter("par", perimetreService.perimetreCourant().getUtilisateurId())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        journalActionService.enregistrer("PRODUIT_CREATION", "crm_produit", id, demande.getCode().trim() + " " + demande.getLibelle().trim());
        return consulter(id);
    }

    @Override
    @Transactional
    public ProduitDTO modifier(Long id, ProduitDemandeDTO demande) {
        ProduitDTO avant = consulter(id);
        if ("ARCHIVE".equals(avant.getStatut())) {
            throw new ErreurMetierException("Ce produit est archivé : réactivez-le pour le modifier.");
        }
        verifierCode(id, demande.getCode());
        em.createNativeQuery("""
                update crm_produit set code = :code, libelle = :libelle, categorie = :categorie, laboratoire = :laboratoire,
                       conditionnement = :conditionnement, prix = :prix, description = :description,
                       modifie_par = :par, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("code", demande.getCode().trim())
                .setParameter("libelle", demande.getLibelle().trim())
                .setParameter("categorie", vide(demande.getCategorie()))
                .setParameter("laboratoire", vide(demande.getLaboratoire()))
                .setParameter("conditionnement", vide(demande.getConditionnement()))
                .setParameter("prix", demande.getPrix())
                .setParameter("description", vide(demande.getDescription()))
                .setParameter("par", perimetreService.perimetreCourant().getUtilisateurId())
                .setParameter("id", id)
                .executeUpdate();
        ProduitDTO apres = consulter(id);
        journalActionService.enregistrer("PRODUIT_MODIFICATION", "crm_produit", id, new EcartsJournal()
                .champ("Code", avant.getCode(), apres.getCode())
                .champ("Libellé", avant.getLibelle(), apres.getLibelle())
                .champ("Catégorie", avant.getCategorie(), apres.getCategorie())
                .champ("Laboratoire", avant.getLaboratoire(), apres.getLaboratoire())
                .champ("Conditionnement", avant.getConditionnement(), apres.getConditionnement())
                .champ("Prix", avant.getPrix(), apres.getPrix())
                .masque("Description", avant.getDescription(), apres.getDescription())
                .detail(avant.getCode() + " " + avant.getLibelle()));
        return apres;
    }

    @Override
    @Transactional
    public ProduitDTO archiver(Long id) {
        return changerStatut(id, "ARCHIVE", "PRODUIT_ARCHIVAGE");
    }

    @Override
    @Transactional
    public ProduitDTO reactiver(Long id) {
        return changerStatut(id, "ACTIF", "PRODUIT_REACTIVATION");
    }

    private ProduitDTO changerStatut(Long id, String statut, String action) {
        ProduitDTO produit = consulter(id);
        em.createNativeQuery("update crm_produit set statut = :statut, modifie_par = :par, updated_at = utc_timestamp() where id = :id")
                .setParameter("statut", statut).setParameter("par", perimetreService.perimetreCourant().getUtilisateurId())
                .setParameter("id", id).executeUpdate();
        journalActionService.enregistrer(action, "crm_produit", id, produit.getCode() + " " + produit.getLibelle());
        return consulter(id);
    }

    @Override
    @Transactional
    public ProduitDTO changerDisponibilite(Long id, boolean disponible) {
        ProduitDTO produit = consulter(id);
        String disponibilite = disponible ? "DISPONIBLE" : "RUPTURE";
        if ("ARCHIVE".equals(produit.getStatut())) {
            throw new ErreurMetierException("Réactivez d'abord le produit " + produit.getCode() + ".");
        }
        if (disponibilite.equals(produit.getDisponibilite())) {
            throw new ErreurMetierException(disponible ? "Le produit " + produit.getCode() + " est déjà disponible."
                    : "Le produit " + produit.getCode() + " est déjà en rupture.");
        }
        em.createNativeQuery("""
                update crm_produit set disponibilite = :disponibilite, disponibilite_le = utc_timestamp(), modifie_par = :par,
                       updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("disponibilite", disponibilite).setParameter("par", perimetreService.perimetreCourant().getUtilisateurId())
                .setParameter("id", id).executeUpdate();
        journalActionService.enregistrer(disponible ? "PRODUIT_RETOUR_STOCK" : "PRODUIT_RUPTURE", "crm_produit", id,
                produit.getCode() + " " + produit.getLibelle());
        return consulter(id);
    }

    @Override
    @Transactional(readOnly = true)
    public void verifierAnnoncables(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("select id as id, code as code, statut as statut, disponibilite as disponibilite "
                + "from crm_produit where id in (:ids)", Tuple.class).setParameter("ids", ids).getResultList();
        if (lignes.size() < ids.size()) {
            throw new ErreurMetierException("Un des produits annoncés n'existe pas.");
        }
        for (Tuple t : lignes) {
            if (!"ACTIF".equals(t.get("statut", String.class))) {
                throw new ErreurMetierException("Le produit " + t.get("code", String.class) + " est archivé : retirez-le du message.");
            }
            if (!"DISPONIBLE".equals(t.get("disponibilite", String.class))) {
                throw new ErreurMetierException("Le produit " + t.get("code", String.class) + " est de nouveau en rupture : retirez-le du message.");
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> valeursCampagne(List<Long> ids) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select libelle as libelle, conditionnement as conditionnement from crm_produit
                where id in (:ids) and statut = 'ACTIF' and disponibilite = 'DISPONIBLE' order by libelle
                """, Tuple.class).setParameter("ids", ids).getResultList();
        return Map.of("calcul.produits_disponibles", lignes.stream()
                .map(t -> "• " + t.get("libelle", String.class) + (t.get("conditionnement") == null ? "" : ", " + t.get("conditionnement", String.class)))
                .collect(Collectors.joining("\n")));
    }

    private LocalDateTime retourDepuis() {
        return LocalDateTime.now(ZoneOffset.UTC).minusDays(parametreService.entier("marketing.retour_stock_jours", 15));
    }

    /** Un code reste unique, archives compris : un produit archive se reactive plutot que d'etre ressaisi. */
    private void verifierCode(Long id, String code) {
        @SuppressWarnings("unchecked")
        List<Tuple> doublons = em.createNativeQuery("select libelle as libelle, statut as statut from crm_produit where code = :code and id <> :id",
                Tuple.class).setParameter("code", code.trim()).setParameter("id", id == null ? 0L : id).getResultList();
        if (!doublons.isEmpty()) {
            Tuple d = doublons.getFirst();
            throw new ErreurMetierException("Le code " + code.trim() + " est déjà utilisé par « " + d.get("libelle", String.class) + " »"
                    + ("ARCHIVE".equals(d.get("statut", String.class)) ? ", produit archivé : réactivez-le." : "."));
        }
    }

    private List<String> valeursDistinctes(String colonne) {
        @SuppressWarnings("unchecked")
        List<String> valeurs = em.createNativeQuery("select distinct " + colonne + " from crm_produit where " + colonne
                + " is not null and statut = 'ACTIF' order by " + colonne).getResultList();
        return valeurs;
    }

    private static String vide(String texte) {
        return texte == null || texte.isBlank() ? null : texte.trim();
    }

    ProduitDTO mapTupleToDto(Tuple tuple) {
        LocalDateTime disponibiliteLe = tuple.get("disponibiliteLe", LocalDateTime.class);
        String disponibilite = tuple.get("disponibilite", String.class);
        return ProduitDTO.builder()
                .disponibilite(disponibilite)
                .disponibiliteLe(disponibiliteLe)
                .deRetour("DISPONIBLE".equals(disponibilite) && disponibiliteLe != null && disponibiliteLe.isAfter(retourDepuis()))
                .id(tuple.get("id", Long.class))
                .code(tuple.get("code", String.class))
                .libelle(tuple.get("libelle", String.class))
                .categorie(tuple.get("categorie", String.class))
                .laboratoire(tuple.get("laboratoire", String.class))
                .conditionnement(tuple.get("conditionnement", String.class))
                .prix(tuple.get("prix", BigDecimal.class))
                .description(tuple.get("description", String.class))
                .statut(tuple.get("statut", String.class))
                .importe(tuple.get("importId") != null)
                .creePar(tuple.get("creePar", String.class))
                .creeLe(tuple.get("creeLe", LocalDateTime.class))
                .modifiePar(tuple.get("modifiePar", String.class))
                .modifieLe(tuple.get("modifieLe", LocalDateTime.class))
                .build();
    }
}
