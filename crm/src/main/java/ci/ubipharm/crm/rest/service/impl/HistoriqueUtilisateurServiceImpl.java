package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.HistoriqueUtilisateurService;
import ci.ubipharm.crm.rest.service.dto.ActionJournalDTO;
import ci.ubipharm.crm.rest.service.dto.ConnexionHistoriqueDTO;
import ci.ubipharm.crm.rest.service.dto.HistoriqueCriteresDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HistoriqueUtilisateurServiceImpl implements HistoriqueUtilisateurService {

    private final EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public PageDTO<ConnexionHistoriqueDTO> connexions(Long utilisateurId, HistoriqueCriteresDTO criteres) {
        // Un echec ne connait que le login saisi : on retrouve ceux du compte par son login
        StringBuilder where = new StringBuilder(" where (j.utilisateur_id = :id or j.login = :login)");
        Map<String, Object> parametres = new LinkedHashMap<>();
        parametres.put("id", utilisateurId);
        parametres.put("login", login(utilisateurId));
        periode(where, parametres, criteres);
        if (criteres.getSucces() != null) {
            where.append(" and j.succes = :succes");
            parametres.put("succes", criteres.getSucces());
        }
        long total = compter("select count(*) from crm_journal_connexion j" + where, parametres);
        List<Tuple> lignes = page("""
                select j.id as id, j.login as login, j.succes as succes, j.adresse_ip as adresseIp, j.navigateur as navigateur, j.created_at as le
                from crm_journal_connexion j""" + where + " order by j.created_at desc, j.id desc", parametres, criteres);
        return PageDTO.<ConnexionHistoriqueDTO>builder().elements(lignes.stream().map(this::mapTupleToConnexion).toList())
                .total(total).page(criteres.getPage()).taille(criteres.getTaille()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageDTO<ActionJournalDTO> actions(Long utilisateurId, HistoriqueCriteresDTO criteres) {
        login(utilisateurId);
        StringBuilder where = new StringBuilder(" where j.utilisateur_id = :id");
        Map<String, Object> parametres = new LinkedHashMap<>();
        parametres.put("id", utilisateurId);
        periode(where, parametres, criteres);
        if (criteres.getAction() != null && !criteres.getAction().isBlank()) {
            where.append(" and j.action like :action");
            parametres.put("action", criteres.getAction().trim().toUpperCase() + "%");
        }
        long total = compter("select count(*) from crm_journal_action j" + where, parametres);
        List<Tuple> lignes = page("""
                select j.id as id, j.login as login, u.nom_complet as utilisateurNom, j.action as action, j.entite as entite, j.entite_id as entiteId,
                       j.details as details, j.adresse_ip as adresseIp, j.created_at as le
                from crm_journal_action j left join crm_utilisateur u on u.id = j.utilisateur_id""" + where + " order by j.created_at desc, j.id desc",
                parametres, criteres);
        return PageDTO.<ActionJournalDTO>builder().elements(lignes.stream().map(this::mapTupleToAction).toList())
                .total(total).page(criteres.getPage()).taille(criteres.getTaille()).build();
    }

    private String login(Long utilisateurId) {
        @SuppressWarnings("unchecked")
        List<String> logins = em.createNativeQuery("select login from crm_utilisateur where id = :id").setParameter("id", utilisateurId).getResultList();
        if (logins.isEmpty()) {
            throw new RessourceIntrouvableException("Utilisateur introuvable.");
        }
        return logins.getFirst();
    }

    private static void periode(StringBuilder where, Map<String, Object> parametres, HistoriqueCriteresDTO criteres) {
        if (criteres.getDu() != null) {
            where.append(" and j.created_at >= :du");
            parametres.put("du", criteres.getDu().atStartOfDay());
        }
        if (criteres.getAu() != null) {
            where.append(" and j.created_at < :au");
            parametres.put("au", criteres.getAu().plusDays(1).atStartOfDay());
        }
    }

    private long compter(String sql, Map<String, Object> parametres) {
        Query requete = em.createNativeQuery(sql);
        parametres.forEach(requete::setParameter);
        return ((Number) requete.getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    private List<Tuple> page(String sql, Map<String, Object> parametres, HistoriqueCriteresDTO criteres) {
        Query requete = em.createNativeQuery(sql, Tuple.class);
        parametres.forEach(requete::setParameter);
        requete.setFirstResult(criteres.getPage() * criteres.getTaille());
        requete.setMaxResults(criteres.getTaille());
        return requete.getResultList();
    }

    ConnexionHistoriqueDTO mapTupleToConnexion(Tuple tuple) {
        return ConnexionHistoriqueDTO.builder()
                .id(tuple.get("id", Long.class))
                .login(tuple.get("login", String.class))
                .succes(Boolean.TRUE.equals(tuple.get("succes", Boolean.class)))
                .adresseIp(tuple.get("adresseIp", String.class))
                .navigateur(tuple.get("navigateur", String.class))
                .le(tuple.get("le", LocalDateTime.class))
                .build();
    }

    ActionJournalDTO mapTupleToAction(Tuple tuple) {
        return ActionJournalDTO.builder()
                .id(tuple.get("id", Long.class))
                .login(tuple.get("login", String.class))
                .utilisateurNom(tuple.get("utilisateurNom", String.class))
                .action(tuple.get("action", String.class))
                .entite(tuple.get("entite", String.class))
                .entiteId(tuple.get("entiteId", Long.class))
                .details(tuple.get("details", String.class))
                .adresseIp(tuple.get("adresseIp", String.class))
                .le(tuple.get("le", LocalDateTime.class))
                .build();
    }
}
