package ci.ubipharm.crm.commun.rest.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.commun.rest.service.ImportService;
import ci.ubipharm.crm.commun.rest.service.dto.ImportCriteresDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ImportDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ImportLigneCriteresDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ImportLigneDTO;
import ci.ubipharm.crm.securite.rest.service.JournalActionService;
import ci.ubipharm.crm.securite.rest.service.PerimetreService;
import ci.ubipharm.crm.securite.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class ImportServiceImpl implements ImportService {

    private static final String SELECT = """
            select i.id as id, i.type as type, i.nom_fichier as nomFichier, i.statut as statut,
                   i.nb_lignes as nbLignes, i.nb_creations as nbCreations, i.nb_mises_a_jour as nbMisesAJour,
                   i.nb_identiques as nbIdentiques, i.nb_rejets as nbRejets,
                   i.nb_crees as nbCrees, i.nb_mis_a_jour as nbMisAJour, i.nb_ignores as nbIgnores,
                   u.nom_complet as auteur, i.created_at as creeLe, v.nom_complet as validePar, i.valide_le as valideLe
            from crm_import i
            join crm_utilisateur u on u.id = i.utilisateur_id
            left join crm_utilisateur v on v.id = i.valide_par
            """;

    private static final TypeReference<Map<String, String>> TYPE_DONNEES = new TypeReference<>() {
    };

    private final EntityManager em;
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper jsonMapper;
    private final PerimetreService perimetreService;
    private final JournalActionService journalActionService;

    @Override
    @Transactional
    public ImportDTO simulation(String type, String nomFichier, List<ImportLigneDTO> lignes) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        em.createNativeQuery("""
                insert into crm_import (type, nom_fichier, statut, nb_lignes, nb_creations, nb_mises_a_jour,
                                        nb_identiques, nb_rejets, utilisateur_id, created_at)
                values (:type, :nomFichier, 'SIMULE', :nbLignes, :nbCreations, :nbMisesAJour, :nbIdentiques, :nbRejets,
                        :utilisateurId, utc_timestamp())
                """)
                .setParameter("type", type)
                .setParameter("nomFichier", tronquer(nomFichier, 255))
                .setParameter("nbLignes", lignes.size())
                .setParameter("nbCreations", compter(lignes, "CREATION"))
                .setParameter("nbMisesAJour", compter(lignes, "MISE_A_JOUR"))
                .setParameter("nbIdentiques", compter(lignes, "IDENTIQUE"))
                .setParameter("nbRejets", compter(lignes, "REJET"))
                .setParameter("utilisateurId", perimetre.getUtilisateurId())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();

        SqlParameterSource[] parametres = lignes.stream()
                .map(l -> new MapSqlParameterSource()
                        .addValue("importId", id)
                        .addValue("numero", l.getNumeroLigne())
                        .addValue("cle", tronquer(l.getCle(), 100))
                        .addValue("action", l.getAction())
                        .addValue("message", tronquer(l.getMessage(), 2000))
                        .addValue("donnees", jsonMapper.writeValueAsString(l.getDonnees())))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("""
                insert into crm_import_ligne (import_id, numero_ligne, cle, action, message, donnees)
                values (:importId, :numero, :cle, :action, :message, :donnees)
                """, parametres);

        journalActionService.enregistrer("IMPORT_SIMULATION", "crm_import", id, type + " " + nomFichier + " : " + lignes.size() + " lignes");
        return consulter(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PageDTO<ImportDTO> historique(ImportCriteresDTO criteres) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> parametres = new LinkedHashMap<>();
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (!perimetre.isVisionGlobale()) {
            where.append(" and i.utilisateur_id = :utilisateurId");
            parametres.put("utilisateurId", perimetre.getUtilisateurId());
        }
        if (criteres.getType() != null) {
            where.append(" and i.type = :type");
            parametres.put("type", criteres.getType());
        }

        Query comptage = em.createNativeQuery("select count(*) from crm_import i" + where);
        parametres.forEach(comptage::setParameter);
        long total = ((Number) comptage.getSingleResult()).longValue();

        Query requete = em.createNativeQuery(SELECT + where + " order by i.created_at desc, i.id desc", Tuple.class);
        parametres.forEach(requete::setParameter);
        requete.setFirstResult(criteres.getPage() * criteres.getTaille());
        requete.setMaxResults(criteres.getTaille());
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return PageDTO.<ImportDTO>builder()
                .elements(lignes.stream().map(this::mapTupleToDto).toList())
                .total(total)
                .page(criteres.getPage())
                .taille(criteres.getTaille())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ImportDTO consulter(Long id) {
        verifierAcces(id);
        return mapTupleToDto((Tuple) em.createNativeQuery(SELECT + " where i.id = :id", Tuple.class)
                .setParameter("id", id)
                .getSingleResult());
    }

    @Override
    @Transactional(readOnly = true)
    public PageDTO<ImportLigneDTO> lignes(Long id, ImportLigneCriteresDTO criteres) {
        verifierAcces(id);
        String where = " where l.import_id = :id" + (criteres.getAction() == null ? "" : " and l.action = :action");
        Query comptage = em.createNativeQuery("select count(*) from crm_import_ligne l" + where).setParameter("id", id);
        Query requete = em.createNativeQuery("""
                select l.id as id, l.numero_ligne as numeroLigne, l.cle as cle, l.action as action,
                       l.message as message, l.resultat as resultat, l.donnees as donnees
                from crm_import_ligne l
                """ + where + " order by l.numero_ligne", Tuple.class).setParameter("id", id);
        if (criteres.getAction() != null) {
            comptage.setParameter("action", criteres.getAction());
            requete.setParameter("action", criteres.getAction());
        }
        long total = ((Number) comptage.getSingleResult()).longValue();
        requete.setFirstResult(criteres.getPage() * criteres.getTaille());
        requete.setMaxResults(criteres.getTaille());
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return PageDTO.<ImportLigneDTO>builder()
                .elements(lignes.stream().map(this::mapTupleToLigneDto).toList())
                .total(total)
                .page(criteres.getPage())
                .taille(criteres.getTaille())
                .build();
    }

    @Override
    @Transactional
    public ImportDTO annuler(Long id) {
        verrouillerSimulation(id);
        em.createNativeQuery("update crm_import set statut = 'ANNULE' where id = :id").setParameter("id", id).executeUpdate();
        journalActionService.enregistrer("IMPORT_ANNULATION", "crm_import", id, null);
        return consulter(id);
    }

    @Override
    @Transactional(readOnly = true)
    public String type(Long id) {
        verifierAcces(id);
        return (String) em.createNativeQuery("select type from crm_import where id = :id").setParameter("id", id).getSingleResult();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ImportLigneDTO> lignesParAction(Long id, String action) {
        verifierAcces(id);
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select l.id as id, l.numero_ligne as numeroLigne, l.cle as cle, l.action as action,
                       l.message as message, l.resultat as resultat, l.donnees as donnees
                from crm_import_ligne l where l.import_id = :id and l.action = :action order by l.numero_ligne
                """, Tuple.class).setParameter("id", id).setParameter("action", action).getResultList();
        return lignes.stream().map(this::mapTupleToLigneDto).toList();
    }

    @Override
    @Transactional
    public ImportDTO majSimulation(Long id, List<ImportLigneDTO> lignes) {
        SqlParameterSource[] parametres = lignes.stream()
                .map(l -> new MapSqlParameterSource()
                        .addValue("id", l.getId())
                        .addValue("cle", tronquer(l.getCle(), 100))
                        .addValue("action", l.getAction())
                        .addValue("message", tronquer(l.getMessage(), 2000))
                        .addValue("donnees", jsonMapper.writeValueAsString(l.getDonnees())))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("update crm_import_ligne set cle = :cle, action = :action, message = :message, donnees = :donnees where id = :id",
                parametres);
        em.createNativeQuery("""
                update crm_import set nb_creations = :nbCreations, nb_mises_a_jour = :nbMisesAJour,
                       nb_identiques = :nbIdentiques, nb_rejets = :nbRejets
                where id = :id
                """)
                .setParameter("nbCreations", compter(lignes, "CREATION"))
                .setParameter("nbMisesAJour", compter(lignes, "MISE_A_JOUR"))
                .setParameter("nbIdentiques", compter(lignes, "IDENTIQUE"))
                .setParameter("nbRejets", compter(lignes, "REJET"))
                .setParameter("id", id)
                .executeUpdate();
        return consulter(id);
    }

    @Override
    @Transactional
    public List<ImportLigneDTO> lignesAValider(Long id, String type) {
        String typeImport = verrouillerSimulation(id);
        if (!typeImport.equals(type)) {
            throw new RessourceIntrouvableException("Import introuvable");
        }
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select l.id as id, l.numero_ligne as numeroLigne, l.cle as cle, l.action as action,
                       l.message as message, l.resultat as resultat, l.donnees as donnees
                from crm_import_ligne l where l.import_id = :id order by l.numero_ligne
                """, Tuple.class).setParameter("id", id).getResultList();
        return lignes.stream().map(this::mapTupleToLigneDto).toList();
    }

    @Override
    @Transactional
    public ImportDTO terminerValidation(Long id, List<ImportLigneDTO> lignes) {
        SqlParameterSource[] parametres = lignes.stream()
                .map(l -> new MapSqlParameterSource()
                        .addValue("id", l.getId())
                        .addValue("resultat", l.getResultat())
                        .addValue("message", tronquer(l.getMessage(), 2000)))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("update crm_import_ligne set resultat = :resultat, message = :message where id = :id", parametres);

        int crees = (int) lignes.stream().filter(l -> "CREE".equals(l.getResultat())).count();
        int misAJour = (int) lignes.stream().filter(l -> "MIS_A_JOUR".equals(l.getResultat())).count();
        em.createNativeQuery("""
                update crm_import set statut = 'VALIDE', nb_crees = :crees, nb_mis_a_jour = :misAJour, nb_ignores = :ignores,
                       valide_par = :utilisateurId, valide_le = utc_timestamp()
                where id = :id
                """)
                .setParameter("crees", crees)
                .setParameter("misAJour", misAJour)
                .setParameter("ignores", lignes.size() - crees - misAJour)
                .setParameter("utilisateurId", perimetreService.perimetreCourant().getUtilisateurId())
                .setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("IMPORT_VALIDATION", "crm_import", id,
                crees + " créations, " + misAJour + " mises à jour, " + (lignes.size() - crees - misAJour) + " ignorées");
        return consulter(id);
    }

    /** Un import n'est visible que par son auteur, ou par un profil a vision globale. */
    private void verifierAcces(Long id) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        @SuppressWarnings("unchecked")
        List<Number> auteurs = em.createNativeQuery("select utilisateur_id from crm_import where id = :id")
                .setParameter("id", id)
                .getResultList();
        if (auteurs.isEmpty() || (!perimetre.isVisionGlobale() && auteurs.getFirst().longValue() != perimetre.getUtilisateurId())) {
            throw new RessourceIntrouvableException("Import introuvable");
        }
    }

    /** Pose un verrou sur l'import pour qu'il ne soit valide ou annule qu'une seule fois. */
    private String verrouillerSimulation(Long id) {
        verifierAcces(id);
        Tuple importCourant = (Tuple) em.createNativeQuery("select type as type, statut as statut from crm_import where id = :id for update", Tuple.class)
                .setParameter("id", id)
                .getSingleResult();
        String statut = importCourant.get("statut", String.class);
        if (!"SIMULE".equals(statut)) {
            throw new ErreurMetierException("Cet import est déjà " + ("VALIDE".equals(statut) ? "validé." : "annulé."));
        }
        return importCourant.get("type", String.class);
    }

    ImportDTO mapTupleToDto(Tuple tuple) {
        return ImportDTO.builder()
                .id(tuple.get("id", Long.class))
                .type(tuple.get("type", String.class))
                .nomFichier(tuple.get("nomFichier", String.class))
                .statut(tuple.get("statut", String.class))
                .nbLignes(tuple.get("nbLignes", Integer.class))
                .nbCreations(tuple.get("nbCreations", Integer.class))
                .nbMisesAJour(tuple.get("nbMisesAJour", Integer.class))
                .nbIdentiques(tuple.get("nbIdentiques", Integer.class))
                .nbRejets(tuple.get("nbRejets", Integer.class))
                .nbCrees(tuple.get("nbCrees", Integer.class))
                .nbMisAJour(tuple.get("nbMisAJour", Integer.class))
                .nbIgnores(tuple.get("nbIgnores", Integer.class))
                .auteur(tuple.get("auteur", String.class))
                .creeLe(tuple.get("creeLe", LocalDateTime.class))
                .validePar(tuple.get("validePar", String.class))
                .valideLe(tuple.get("valideLe", LocalDateTime.class))
                .build();
    }

    ImportLigneDTO mapTupleToLigneDto(Tuple tuple) {
        return ImportLigneDTO.builder()
                .id(tuple.get("id", Long.class))
                .numeroLigne(tuple.get("numeroLigne", Integer.class))
                .cle(tuple.get("cle", String.class))
                .action(tuple.get("action", String.class))
                .message(tuple.get("message", String.class))
                .resultat(tuple.get("resultat", String.class))
                .donnees(jsonMapper.readValue(tuple.get("donnees", String.class), TYPE_DONNEES))
                .build();
    }

    private int compter(List<ImportLigneDTO> lignes, String action) {
        return (int) lignes.stream().filter(l -> action.equals(l.getAction())).count();
    }

    private String tronquer(String texte, int longueur) {
        return texte == null || texte.length() <= longueur ? texte : texte.substring(0, longueur - 1) + "…";
    }
}
