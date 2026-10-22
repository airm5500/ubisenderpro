package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.ReferentielService;
import ci.ubipharm.crm.rest.service.ResolutionReferentiels;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.ReferentielDTO;
import ci.ubipharm.crm.rest.service.dto.ReferentielDemandeDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReferentielServiceImpl implements ReferentielService {

    static final Set<String> TYPES = Set.of("REGION", "VILLE", "COMMUNE", "TOURNEE");
    private static final Map<String, String> LIBELLES = Map.of("REGION", "région", "VILLE", "ville", "COMMUNE", "commune", "TOURNEE", "tournée");

    private static final String SELECT = """
            select r.id as id, r.type_referentiel as type, r.code as code, r.libelle as libelle, r.parent_id as parentId,
                   p.libelle as parentLibelle, r.agence_id as agenceId, a.libelle as agenceLibelle, r.statut as statut,
                   (select count(*) from crm_client c where c.statut <> 'ARCHIVE' and (c.region_id = r.id or c.ville_id = r.id
                        or c.commune_id = r.id or c.tournee_id = r.id)) as nbClients,
                   u.nom_complet as modifiePar, r.updated_at as modifieLe
            from crm_referentiel r
            left join crm_referentiel p on p.id = r.parent_id
            left join crm_agence a on a.id = r.agence_id
            left join crm_utilisateur u on u.id = r.modifie_par
            """;

    private final EntityManager em;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;

    @Override
    @Transactional(readOnly = true)
    public List<ReferentielDTO> lister(String type, boolean archives) {
        StringBuilder where = new StringBuilder(" where r.statut = :statut");
        Map<String, Object> parametres = new LinkedHashMap<>();
        parametres.put("statut", archives ? "ARCHIVE" : "ACTIF");
        if (type != null && !type.isBlank()) {
            where.append(" and r.type_referentiel = :type");
            parametres.put("type", verifierType(type));
        }
        Query requete = em.createNativeQuery(SELECT + where + " order by r.type_referentiel, p.libelle, r.libelle", Tuple.class);
        parametres.forEach(requete::setParameter);
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ResolutionReferentiels resolution() {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select r.id as id, r.type_referentiel as type, r.code as code, r.libelle as libelle, r.parent_id as parentId,
                       p.libelle as parentLibelle, r.agence_id as agenceId, a.libelle as agenceLibelle, r.statut as statut,
                       0 as nbClients, null as modifiePar, r.updated_at as modifieLe
                from crm_referentiel r
                left join crm_referentiel p on p.id = r.parent_id
                left join crm_agence a on a.id = r.agence_id
                """, Tuple.class).getResultList();
        return new ResolutionReferentiels(lignes.stream().map(this::mapTupleToDto).toList());
    }

    @Override
    @Transactional
    public ReferentielDTO creer(ReferentielDemandeDTO demande) {
        String type = verifierType(demande.getType());
        Saisie s = verifier(null, type, demande);
        em.createNativeQuery("""
                insert into crm_referentiel (type_referentiel, code, libelle, parent_id, agence_id, statut, modifie_par, created_at)
                values (:type, :code, :libelle, :parent, :agence, 'ACTIF', :par, utc_timestamp())
                """)
                .setParameter("type", type).setParameter("code", s.code()).setParameter("libelle", s.libelle())
                .setParameter("parent", s.parentId()).setParameter("agence", s.agenceId())
                .setParameter("par", utilisateurCourantService.utilisateur().getId())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        journalActionService.enregistrer("REFERENTIEL_CREATION", "crm_referentiel", id, LIBELLES.get(type) + " " + s.libelle());
        return consulter(id);
    }

    @Override
    @Transactional
    public ReferentielDTO modifier(Long id, ReferentielDemandeDTO demande) {
        ReferentielDTO avant = consulter(id);
        Saisie s = verifier(id, avant.getType(), demande);
        if ("VILLE".equals(avant.getType()) && s.parentId() != null && !s.parentId().equals(avant.getParentId())) {
            // Les clients de la ville suivent sa nouvelle region
            em.createNativeQuery("update crm_client set region_id = :region where ville_id = :id")
                    .setParameter("region", s.parentId()).setParameter("id", id).executeUpdate();
        }
        if ("COMMUNE".equals(avant.getType()) && !s.parentId().equals(avant.getParentId())) {
            em.createNativeQuery("""
                    update crm_client c join crm_referentiel v on v.id = :ville
                    set c.ville_id = v.id, c.region_id = coalesce(v.parent_id, c.region_id) where c.commune_id = :id
                    """).setParameter("ville", s.parentId()).setParameter("id", id).executeUpdate();
        }
        em.createNativeQuery("""
                update crm_referentiel set code = :code, libelle = :libelle, parent_id = :parent, agence_id = :agence, modifie_par = :par,
                       updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("code", s.code()).setParameter("libelle", s.libelle()).setParameter("parent", s.parentId())
                .setParameter("agence", s.agenceId()).setParameter("par", utilisateurCourantService.utilisateur().getId())
                .setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("REFERENTIEL_MODIFICATION", "crm_referentiel", id, LIBELLES.get(avant.getType()) + " "
                + avant.getLibelle() + (avant.getLibelle().equals(s.libelle()) ? "" : " -> " + s.libelle()));
        return consulter(id);
    }

    @Override
    @Transactional
    public ReferentielDTO archiver(Long id) {
        return changerStatut(id, "ARCHIVE");
    }

    @Override
    @Transactional
    public ReferentielDTO reactiver(Long id) {
        return changerStatut(id, "ACTIF");
    }

    private ReferentielDTO changerStatut(Long id, String statut) {
        ReferentielDTO r = consulter(id);
        if (statut.equals(r.getStatut())) {
            return r;
        }
        if ("ACTIF".equals(statut) && r.getParentId() != null) {
            String parent = (String) em.createNativeQuery("select statut from crm_referentiel where id = :id")
                    .setParameter("id", r.getParentId()).getSingleResult();
            if (!"ACTIF".equals(parent)) {
                throw new ErreurMetierException("Réactivez d'abord « " + r.getParentLibelle() + " ».");
            }
        }
        em.createNativeQuery("update crm_referentiel set statut = :statut, modifie_par = :par, updated_at = utc_timestamp() where id = :id")
                .setParameter("statut", statut).setParameter("par", utilisateurCourantService.utilisateur().getId()).setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("ARCHIVE".equals(statut) ? "REFERENTIEL_ARCHIVAGE" : "REFERENTIEL_REACTIVATION", "crm_referentiel", id,
                LIBELLES.get(r.getType()) + " " + r.getLibelle());
        return consulter(id);
    }

    private ReferentielDTO consulter(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where r.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Entrée de référentiel introuvable");
        }
        return mapTupleToDto(lignes.getFirst());
    }

    private record Saisie(String code, String libelle, Long parentId, Long agenceId) {
    }

    /** Commune rattachee a une ville, ville eventuellement a une region, tournee a une agence ; pas de doublon sous le meme parent. */
    private Saisie verifier(Long id, String type, ReferentielDemandeDTO demande) {
        String libelle = demande.getLibelle().trim().replaceAll("\\s+", " ");
        String code = demande.getCode() == null || demande.getCode().isBlank() ? null : demande.getCode().trim().toUpperCase(Locale.ROOT);
        Long parentId = null;
        Long agenceId = null;
        switch (type) {
            case "VILLE", "COMMUNE" -> {
                String typeParent = "VILLE".equals(type) ? "REGION" : "VILLE";
                if (demande.getParentId() == null) {
                    if ("COMMUNE".equals(type)) {
                        throw new ErreurMetierException("Choisissez la ville de la commune.");
                    }
                } else {
                    Number ok = (Number) em.createNativeQuery("select count(*) from crm_referentiel where id = :id and type_referentiel = :type and statut = 'ACTIF'")
                            .setParameter("id", demande.getParentId()).setParameter("type", typeParent).getSingleResult();
                    if (ok.longValue() == 0) {
                        throw new ErreurMetierException("VILLE".equals(type) ? "Région inconnue ou archivée." : "Ville inconnue ou archivée.");
                    }
                    parentId = demande.getParentId();
                }
            }
            case "TOURNEE" -> {
                if (demande.getAgenceId() == null) {
                    throw new ErreurMetierException("Choisissez l'agence qui dessert la tournée.");
                }
                Number ok = (Number) em.createNativeQuery("select count(*) from crm_agence where id = :id and statut = 'ACTIF'")
                        .setParameter("id", demande.getAgenceId()).getSingleResult();
                if (ok.longValue() == 0) {
                    throw new ErreurMetierException("Agence inconnue ou archivée.");
                }
                agenceId = demande.getAgenceId();
            }
            default -> {
            }
        }
        Number doublons = (Number) em.createNativeQuery("""
                select count(*) from crm_referentiel where type_referentiel = :type and id <> :id
                  and (lower(libelle) = lower(:libelle) and coalesce(parent_id, 0) = :parent and coalesce(agence_id, 0) = :agence
                       or :code is not null and upper(code) = :code)
                """)
                .setParameter("type", type).setParameter("id", id == null ? 0L : id).setParameter("libelle", libelle)
                .setParameter("parent", parentId == null ? 0L : parentId).setParameter("agence", agenceId == null ? 0L : agenceId)
                .setParameter("code", code)
                .getSingleResult();
        if (doublons.longValue() > 0) {
            throw new ErreurMetierException("Cette " + LIBELLES.get(type) + " existe déjà (même nom ou même code), peut-être parmi les archivées.");
        }
        return new Saisie(code, libelle, parentId, agenceId);
    }

    static String verifierType(String type) {
        String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(t)) {
            throw new ErreurMetierException("Type de référentiel inconnu : " + type + ".");
        }
        return t;
    }

    ReferentielDTO mapTupleToDto(Tuple tuple) {
        return ReferentielDTO.builder()
                .id(((Number) tuple.get("id")).longValue())
                .type(tuple.get("type", String.class))
                .code(tuple.get("code", String.class))
                .libelle(tuple.get("libelle", String.class))
                .parentId(tuple.get("parentId") == null ? null : ((Number) tuple.get("parentId")).longValue())
                .parentLibelle(tuple.get("parentLibelle", String.class))
                .agenceId(tuple.get("agenceId") == null ? null : ((Number) tuple.get("agenceId")).longValue())
                .agenceLibelle(tuple.get("agenceLibelle", String.class))
                .statut(tuple.get("statut", String.class))
                .nbClients(((Number) tuple.get("nbClients")).longValue())
                .modifiePar(tuple.get("modifiePar", String.class))
                .modifieLe(tuple.get("modifieLe", LocalDateTime.class))
                .build();
    }
}
