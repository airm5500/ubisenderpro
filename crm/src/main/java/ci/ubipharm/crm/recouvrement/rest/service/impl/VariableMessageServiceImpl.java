package ci.ubipharm.crm.recouvrement.rest.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.recouvrement.rest.service.VariableMessageService;
import ci.ubipharm.crm.recouvrement.rest.service.dto.SourceVariableDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.VariableMessageDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.VariableMessageDemandeDTO;
import ci.ubipharm.crm.securite.rest.service.JournalActionService;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

/**
 * Variables des modeles (cahier des charges 4.9). Les sources client.* et facture.* sont lues dans le schema :
 * une colonne ajoutee a crm_client ou crm_facture devient une variable possible sans developpement.
 */
@Service
@RequiredArgsConstructor
public class VariableMessageServiceImpl implements VariableMessageService {

    /** Valeurs calculees a partir des tables liees. */
    static final Map<String, String> SOURCES_CALCULEES = new LinkedHashMap<>();

    static {
        SOURCES_CALCULEES.put("calcul.agence", "Agence du client");
        SOURCES_CALCULEES.put("calcul.segment", "Segment du client");
        SOURCES_CALCULEES.put("calcul.sous_type", "Sous-type de compte de la facture");
        SOURCES_CALCULEES.put("calcul.jours_retard", "Jours de retard a la date d'execution");
        SOURCES_CALCULEES.put("calcul.jours_avant_echeance", "Jours avant l'echeance a la date d'execution");
        SOURCES_CALCULEES.put("calcul.agent_nom", "Nom de l'agent du client");
        SOURCES_CALCULEES.put("calcul.agent_numero", "Numero WhatsApp de l'agent du client");
    }

    /** Colonnes techniques jamais proposees. */
    private static final Set<String> COLONNES_TECHNIQUES = Set.of("id", "created_at", "updated_at", "statut", "agence_id", "client_id",
            "sous_type_id", "import_id", "segmentation");

    private static final String SELECT = """
            select id as id, code as code, libelle as libelle, source as source, exemple as exemple, statut as statut
            from crm_variable_message
            """;

    private final EntityManager em;
    private final JournalActionService journalActionService;

    @Override
    @Transactional(readOnly = true)
    public List<VariableMessageDTO> variables() {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where statut = 'ACTIF' order by libelle", Tuple.class).getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SourceVariableDTO> sources() {
        List<SourceVariableDTO> sources = new ArrayList<>();
        colonnes("crm_client").forEach(c -> sources.add(SourceVariableDTO.builder().source("client." + c).libelle("Client : " + c).build()));
        colonnes("crm_facture").forEach(c -> sources.add(SourceVariableDTO.builder().source("facture." + c).libelle("Facture : " + c).build()));
        SOURCES_CALCULEES.forEach((source, libelle) -> sources.add(SourceVariableDTO.builder().source(source).libelle(libelle).build()));
        return sources;
    }

    @Override
    @Transactional
    public VariableMessageDTO creer(VariableMessageDemandeDTO demande) {
        verifierSource(demande.getSource());
        if (((Number) em.createNativeQuery("select count(*) from crm_variable_message where code = :code")
                .setParameter("code", demande.getCode()).getSingleResult()).longValue() > 0) {
            throw new ErreurMetierException("La variable {" + demande.getCode() + "} existe deja.");
        }
        em.createNativeQuery("""
                insert into crm_variable_message (code, libelle, source, exemple, created_at)
                values (:code, :libelle, :source, :exemple, utc_timestamp())
                """)
                .setParameter("code", demande.getCode())
                .setParameter("libelle", demande.getLibelle().trim())
                .setParameter("source", demande.getSource())
                .setParameter("exemple", demande.getExemple())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        journalActionService.enregistrer("VARIABLE_CREATION", "crm_variable_message", id, "{" + demande.getCode() + "} = " + demande.getSource());
        return consulter(id);
    }

    @Override
    @Transactional
    public VariableMessageDTO modifier(Long id, VariableMessageDemandeDTO demande) {
        VariableMessageDTO existante = consulter(id);
        verifierSource(demande.getSource());
        if (!existante.getCode().equals(demande.getCode())) {
            // Les modeles citent la variable par son code : le renommer casserait leur texte
            throw new ErreurMetierException("Le code d'une variable ne change pas ; creez une nouvelle variable.");
        }
        em.createNativeQuery("""
                update crm_variable_message set libelle = :libelle, source = :source, exemple = :exemple, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("libelle", demande.getLibelle().trim())
                .setParameter("source", demande.getSource())
                .setParameter("exemple", demande.getExemple())
                .setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("VARIABLE_MODIFICATION", "crm_variable_message", id, "{" + demande.getCode() + "} = " + demande.getSource());
        return consulter(id);
    }

    @Override
    @Transactional
    public VariableMessageDTO archiver(Long id) {
        VariableMessageDTO variable = consulter(id);
        @SuppressWarnings("unchecked")
        List<String> modeles = em.createNativeQuery("select nom from crm_modele_message where statut = 'ACTIF' and contenu like :motif order by nom")
                .setParameter("motif", "%{" + variable.getCode() + "}%")
                .getResultList();
        if (!modeles.isEmpty()) {
            throw new ErreurMetierException("La variable {" + variable.getCode() + "} est utilisee par " + String.join(", ", modeles) + ".");
        }
        em.createNativeQuery("update crm_variable_message set statut = 'ARCHIVE', updated_at = utc_timestamp() where id = :id")
                .setParameter("id", id).executeUpdate();
        journalActionService.enregistrer("VARIABLE_ARCHIVAGE", "crm_variable_message", id, "{" + variable.getCode() + "}");
        return consulter(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> sourcesParCode() {
        Map<String, String> sources = new LinkedHashMap<>();
        variables().forEach(v -> sources.put(v.getCode(), v.getSource()));
        return sources;
    }

    /** Colonnes proposables d'une table, lues dans le schema courant. */
    List<String> colonnes(String table) {
        @SuppressWarnings("unchecked")
        List<String> colonnes = em.createNativeQuery("""
                select column_name from information_schema.columns
                where table_schema = database() and table_name = :table order by ordinal_position
                """).setParameter("table", table).getResultList();
        return colonnes.stream().map(String::toLowerCase).filter(c -> !COLONNES_TECHNIQUES.contains(c) && !c.endsWith("_hash")).toList();
    }

    private void verifierSource(String source) {
        if (sources().stream().noneMatch(s -> s.getSource().equals(source))) {
            throw new ErreurMetierException("Champ inconnu : " + source + ".");
        }
    }

    private VariableMessageDTO consulter(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Variable introuvable");
        }
        return mapTupleToDto(lignes.getFirst());
    }

    VariableMessageDTO mapTupleToDto(Tuple tuple) {
        return VariableMessageDTO.builder()
                .id(tuple.get("id", Long.class))
                .code(tuple.get("code", String.class))
                .libelle(tuple.get("libelle", String.class))
                .source(tuple.get("source", String.class))
                .exemple(tuple.get("exemple", String.class))
                .statut(tuple.get("statut", String.class))
                .build();
    }
}
