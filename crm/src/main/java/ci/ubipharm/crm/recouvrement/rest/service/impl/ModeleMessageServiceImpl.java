package ci.ubipharm.crm.recouvrement.rest.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.recouvrement.rest.service.ModeleMessageService;
import ci.ubipharm.crm.recouvrement.rest.service.VariableMessageService;
import ci.ubipharm.crm.recouvrement.rest.service.dto.ModeleMessageCriteresDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.ModeleMessageDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.ModeleMessageDemandeDTO;
import ci.ubipharm.crm.securite.rest.service.JournalActionService;
import ci.ubipharm.crm.securite.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

/** Modeles de messages : la Direction du Recouvrement les ecrit, les agents ne font que les utiliser (4.8). */
@Service
@RequiredArgsConstructor
public class ModeleMessageServiceImpl implements ModeleMessageService {

    private static final String SELECT = """
            select m.id as id, m.nom as nom, m.type_campagne as typeCampagne, m.contenu as contenu, m.statut as statut,
                   c.nom_complet as creePar, u.nom_complet as modifiePar, m.created_at as createdAt, m.updated_at as updatedAt
            from crm_modele_message m
            join crm_utilisateur c on c.id = m.cree_par
            left join crm_utilisateur u on u.id = m.modifie_par
            """;

    private final EntityManager em;
    private final VariableMessageService variableMessageService;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;

    @Override
    @Transactional(readOnly = true)
    public List<ModeleMessageDTO> rechercher(ModeleMessageCriteresDTO criteres) {
        StringBuilder where = new StringBuilder(" where m.statut = :statut");
        Map<String, Object> parametres = new LinkedHashMap<>();
        parametres.put("statut", criteres.getStatut() == null ? "ACTIF" : criteres.getStatut());
        if (criteres.getTypeCampagne() != null) {
            where.append(" and m.type_campagne = :typeCampagne");
            parametres.put("typeCampagne", criteres.getTypeCampagne());
        }
        Query requete = em.createNativeQuery(SELECT + where + " order by m.type_campagne, m.nom", Tuple.class);
        parametres.forEach(requete::setParameter);
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ModeleMessageDTO consulter(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where m.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Modèle introuvable");
        }
        return mapTupleToDto(lignes.getFirst());
    }

    @Override
    @Transactional
    public ModeleMessageDTO creer(ModeleMessageDemandeDTO demande) {
        verifier(demande, null);
        em.createNativeQuery("""
                insert into crm_modele_message (nom, type_campagne, contenu, cree_par, created_at)
                values (:nom, :typeCampagne, :contenu, :creePar, utc_timestamp())
                """)
                .setParameter("nom", demande.getNom().trim())
                .setParameter("typeCampagne", demande.getTypeCampagne())
                .setParameter("contenu", demande.getContenu())
                .setParameter("creePar", utilisateurCourantService.utilisateur().getId())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        journalActionService.enregistrer("MODELE_CREATION", "crm_modele_message", id, demande.getNom().trim());
        return consulter(id);
    }

    @Override
    @Transactional
    public ModeleMessageDTO modifier(Long id, ModeleMessageDemandeDTO demande) {
        ModeleMessageDTO existant = consulter(id);
        if ("ARCHIVE".equals(existant.getStatut())) {
            throw new ErreurMetierException("Le modèle « " + existant.getNom() + " » est archivé.");
        }
        verifier(demande, id);
        em.createNativeQuery("""
                update crm_modele_message set nom = :nom, type_campagne = :typeCampagne, contenu = :contenu,
                       modifie_par = :modifiePar, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("nom", demande.getNom().trim())
                .setParameter("typeCampagne", demande.getTypeCampagne())
                .setParameter("contenu", demande.getContenu())
                .setParameter("modifiePar", utilisateurCourantService.utilisateur().getId())
                .setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("MODELE_MODIFICATION", "crm_modele_message", id, demande.getNom().trim());
        return consulter(id);
    }

    @Override
    @Transactional
    public ModeleMessageDTO archiver(Long id) {
        ModeleMessageDTO modele = consulter(id);
        @SuppressWarnings("unchecked")
        List<String> campagnes = em.createNativeQuery("""
                select nom from crm_campagne where modele_id = :id and statut in ('BROUILLON', 'A_VALIDER') order by nom
                """).setParameter("id", id).getResultList();
        if (!campagnes.isEmpty()) {
            throw new ErreurMetierException("Le modèle est utilisé par des campagnes non validées : " + String.join(", ", campagnes) + ".");
        }
        em.createNativeQuery("update crm_modele_message set statut = 'ARCHIVE', modifie_par = :modifiePar, updated_at = utc_timestamp() where id = :id")
                .setParameter("modifiePar", utilisateurCourantService.utilisateur().getId())
                .setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("MODELE_ARCHIVAGE", "crm_modele_message", id, modele.getNom());
        return consulter(id);
    }

    private void verifier(ModeleMessageDemandeDTO demande, Long id) {
        if (!GabaritMessage.TYPES_CAMPAGNE.contains(demande.getTypeCampagne())) {
            throw new ErreurMetierException("Type de relance inconnu : " + demande.getTypeCampagne() + ".");
        }
        Set<String> connues = variableMessageService.sourcesParCode().keySet();
        List<String> inconnues = new ArrayList<>();
        GabaritMessage.variables(demande.getContenu()).stream().filter(v -> !connues.contains(v)).forEach(v -> inconnues.add("{" + v + "}"));
        if (!inconnues.isEmpty()) {
            throw new ErreurMetierException((inconnues.size() == 1 ? "Variable inconnue : " : "Variables inconnues : ") + String.join(", ", inconnues) + ".");
        }
        long homonymes = ((Number) em.createNativeQuery("select count(*) from crm_modele_message where nom = :nom and id <> :id")
                .setParameter("nom", demande.getNom().trim())
                .setParameter("id", id == null ? 0L : id)
                .getSingleResult()).longValue();
        if (homonymes > 0) {
            throw new ErreurMetierException("Un modèle s'appelle déjà « " + demande.getNom().trim() + " ».");
        }
    }

    ModeleMessageDTO mapTupleToDto(Tuple tuple) {
        String contenu = tuple.get("contenu", String.class);
        return ModeleMessageDTO.builder()
                .id(tuple.get("id", Long.class))
                .nom(tuple.get("nom", String.class))
                .typeCampagne(tuple.get("typeCampagne", String.class))
                .contenu(contenu)
                .statut(tuple.get("statut", String.class))
                .variables(List.copyOf(GabaritMessage.variables(contenu)))
                .creePar(tuple.get("creePar", String.class))
                .modifiePar(tuple.get("modifiePar", String.class))
                .createdAt(tuple.get("createdAt", LocalDateTime.class))
                .updatedAt(tuple.get("updatedAt", LocalDateTime.class))
                .build();
    }
}
