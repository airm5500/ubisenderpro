package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.AgenceService;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.AgenceDTO;
import ci.ubipharm.crm.rest.service.dto.AgenceDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.AgenceGestionDTO;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgenceServiceImpl implements AgenceService {

    private final EntityManager em;
    private final PerimetreService perimetreService;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;

    private static final String SELECT_GESTION = """
            select a.id as id, a.code as code, a.code_import as codeImport, a.libelle as libelle, a.statut as statut,
                   (select count(*) from crm_client c where c.agence_id = a.id and c.statut = 'ACTIF') as nbClients,
                   (select count(distinct ua.utilisateur_id) from crm_utilisateur_agence ua join crm_utilisateur u on u.id = ua.utilisateur_id
                    where ua.agence_id = a.id and u.statut = 'ACTIF' and ua.date_debut <= utc_date()
                      and (ua.date_fin is null or ua.date_fin >= utc_date())) as nbUtilisateurs,
                   u.nom_complet as modifiePar, coalesce(a.updated_at, a.created_at) as modifieLe
            from crm_agence a left join crm_utilisateur u on u.id = a.modifie_par
            """;

    @Override
    @Transactional(readOnly = true)
    public List<AgenceDTO> listerVisibles() {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (!perimetre.isVisionGlobale() && perimetre.getAgenceIds().isEmpty()) {
            return List.of();
        }
        String sql = "select a.id as id, a.code as code, a.libelle as libelle from crm_agence a where a.statut = 'ACTIF'"
                + (perimetre.isVisionGlobale() ? "" : " and a.id in (:agences)")
                + " order by a.libelle";
        Query requete = em.createNativeQuery(sql, Tuple.class);
        if (!perimetre.isVisionGlobale()) {
            requete.setParameter("agences", perimetre.getAgenceIds());
        }
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgenceGestionDTO> lister(boolean archives) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT_GESTION + " where a.statut = :statut order by a.libelle", Tuple.class)
                .setParameter("statut", archives ? "ARCHIVE" : "ACTIF").getResultList();
        return lignes.stream().map(this::mapTupleToGestion).toList();
    }

    @Override
    @Transactional
    public AgenceGestionDTO creer(AgenceDemandeDTO demande) {
        verifier(demande, null);
        em.createNativeQuery("""
                insert into crm_agence (code, code_import, libelle, modifie_par, created_at) values (:code, :codeImport, :libelle, :par, utc_timestamp())
                """).setParameter("code", code(demande)).setParameter("codeImport", codeImport(demande)).setParameter("libelle", demande.getLibelle().trim())
                .setParameter("par", utilisateurCourantService.utilisateur().getId()).executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        journalActionService.enregistrer("AGENCE_CREATION", "crm_agence", id, code(demande) + " " + demande.getLibelle().trim());
        return consulter(id);
    }

    @Override
    @Transactional
    public AgenceGestionDTO modifier(Long id, AgenceDemandeDTO demande) {
        AgenceGestionDTO avant = consulter(id);
        verifier(demande, id);
        em.createNativeQuery("""
                update crm_agence set code = :code, code_import = :codeImport, libelle = :libelle, modifie_par = :par, updated_at = utc_timestamp()
                where id = :id
                """).setParameter("code", code(demande)).setParameter("codeImport", codeImport(demande)).setParameter("libelle", demande.getLibelle().trim())
                .setParameter("par", utilisateurCourantService.utilisateur().getId()).setParameter("id", id).executeUpdate();
        journalActionService.enregistrer("AGENCE_MODIFICATION", "crm_agence", id,
                avant.getCode() + " " + avant.getLibelle() + " -> " + code(demande) + " " + demande.getLibelle().trim());
        return consulter(id);
    }

    @Override
    @Transactional
    public AgenceGestionDTO archiver(Long id) {
        AgenceGestionDTO agence = consulter(id);
        if (agence.getNbClients() > 0 || agence.getNbUtilisateurs() > 0) {
            throw new ErreurMetierException("L'agence " + agence.getLibelle() + " a encore " + agence.getNbClients() + " client(s) actif(s) et "
                    + agence.getNbUtilisateurs() + " utilisateur(s) : rattachez-les d'abord à une autre agence.");
        }
        changerStatut(id, "ARCHIVE");
        journalActionService.enregistrer("AGENCE_ARCHIVAGE", "crm_agence", id, agence.getLibelle());
        return consulter(id);
    }

    @Override
    @Transactional
    public AgenceGestionDTO reactiver(Long id) {
        AgenceGestionDTO agence = consulter(id);
        changerStatut(id, "ACTIF");
        journalActionService.enregistrer("AGENCE_REACTIVATION", "crm_agence", id, agence.getLibelle());
        return consulter(id);
    }

    /** Codes et nom uniques, archives compris : les imports et l'historique les citent. */
    private void verifier(AgenceDemandeDTO demande, Long id) {
        long id0 = id == null ? 0L : id;
        if (((Number) em.createNativeQuery("select count(*) from crm_agence where code = :code and id <> :id")
                .setParameter("code", code(demande)).setParameter("id", id0).getSingleResult()).longValue() > 0) {
            throw new ErreurMetierException("Le code " + code(demande) + " est déjà celui d'une autre agence.");
        }
        if (codeImport(demande) != null && ((Number) em.createNativeQuery(
                "select count(*) from crm_agence where (code_import = :code or code = :code) and id <> :id")
                .setParameter("code", codeImport(demande)).setParameter("id", id0).getSingleResult()).longValue() > 0) {
            throw new ErreurMetierException("Le code d'import " + codeImport(demande) + " désigne déjà une autre agence.");
        }
        if (((Number) em.createNativeQuery("select count(*) from crm_agence where libelle = :libelle and id <> :id")
                .setParameter("libelle", demande.getLibelle().trim()).setParameter("id", id0).getSingleResult()).longValue() > 0) {
            throw new ErreurMetierException("Une agence s'appelle déjà « " + demande.getLibelle().trim() + " ».");
        }
    }

    private void changerStatut(Long id, String statut) {
        em.createNativeQuery("update crm_agence set statut = :statut, modifie_par = :par, updated_at = utc_timestamp() where id = :id")
                .setParameter("statut", statut).setParameter("par", utilisateurCourantService.utilisateur().getId()).setParameter("id", id)
                .executeUpdate();
    }

    private AgenceGestionDTO consulter(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT_GESTION + " where a.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Agence introuvable.");
        }
        return mapTupleToGestion(lignes.getFirst());
    }

    private static String code(AgenceDemandeDTO demande) {
        return demande.getCode().trim().toUpperCase(Locale.ROOT);
    }

    private static String codeImport(AgenceDemandeDTO demande) {
        return demande.getCodeImport() == null || demande.getCodeImport().isBlank() ? null : demande.getCodeImport().trim().toUpperCase(Locale.ROOT);
    }

    AgenceGestionDTO mapTupleToGestion(Tuple tuple) {
        return AgenceGestionDTO.builder()
                .id(tuple.get("id", Long.class))
                .code(tuple.get("code", String.class))
                .codeImport(tuple.get("codeImport", String.class))
                .libelle(tuple.get("libelle", String.class))
                .statut(tuple.get("statut", String.class))
                .nbClients(((Number) tuple.get("nbClients")).longValue())
                .nbUtilisateurs(((Number) tuple.get("nbUtilisateurs")).longValue())
                .modifiePar(tuple.get("modifiePar", String.class))
                .modifieLe(tuple.get("modifieLe", LocalDateTime.class))
                .build();
    }

    private AgenceDTO mapTupleToDto(Tuple tuple) {
        return AgenceDTO.builder()
                .id(tuple.get("id", Long.class))
                .code(tuple.get("code", String.class))
                .libelle(tuple.get("libelle", String.class))
                .build();
    }
}
