package ci.ubipharm.crm.recouvrement.rest.service.impl;

import static ci.ubipharm.crm.recouvrement.rest.service.impl.RequetesRecouvrement.AGENT_RECOUVREMENT;
import static ci.ubipharm.crm.recouvrement.rest.service.impl.RequetesRecouvrement.DELEGATION_EN_COURS;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.recouvrement.rest.service.DelegationService;
import ci.ubipharm.crm.recouvrement.rest.service.PortefeuilleService;
import ci.ubipharm.crm.recouvrement.rest.service.dto.DelegationCriteresDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.DelegationDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.DelegationDemandeDTO;
import ci.ubipharm.crm.securite.rest.service.JournalActionService;
import ci.ubipharm.crm.securite.rest.service.PerimetreService;
import ci.ubipharm.crm.securite.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

/**
 * Interim autorise par la Direction du Recouvrement : pendant la periode, le remplacant gere le portefeuille
 * de l'absent et envoie depuis son numero. L'acces s'arrete seul a la date de retour, sans traitement planifie.
 */
@Service
@RequiredArgsConstructor
public class DelegationServiceImpl implements DelegationService {

    private static final DateTimeFormatter DATE_LISIBLE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String SELECT = """
            select d.id as id, d.titulaire_id as titulaireId, t.nom_complet as titulaireNom, t.numero_whatsapp as titulaireNumero,
                   d.remplacant_id as remplacantId, r.nom_complet as remplacantNom, d.date_debut as dateDebut, d.date_fin as dateFin,
                   d.motif as motif, d.statut as statut, (%s) as enCours, p.nom_complet as autorisePar
            from crm_delegation d
            join crm_utilisateur t on t.id = d.titulaire_id
            join crm_utilisateur r on r.id = d.remplacant_id
            join crm_utilisateur p on p.id = d.autorise_par
            """.formatted(DELEGATION_EN_COURS);

    private final EntityManager em;
    private final PerimetreService perimetreService;
    private final PortefeuilleService portefeuilleService;
    private final JournalActionService journalActionService;

    @Override
    @Transactional
    public DelegationDTO creer(DelegationDemandeDTO demande) {
        if (demande.getTitulaireId().equals(demande.getRemplacantId())) {
            throw new ErreurMetierException("Le remplaçant doit être un autre agent.");
        }
        if (demande.getDateFin().isBefore(demande.getDateDebut())) {
            throw new ErreurMetierException("La date de retour précède la date de début.");
        }
        if (demande.getDateFin().isBefore(LocalDate.now(ZoneOffset.UTC))) {
            throw new ErreurMetierException("La date de retour est déjà passée.");
        }
        String titulaire = nomAgent(demande.getTitulaireId());
        String remplacant = nomAgent(demande.getRemplacantId());

        // Verrou sur l'agent absent : deux delegations qui se chevauchent ne peuvent pas passer en meme temps
        em.createNativeQuery("select id from crm_utilisateur where id = :id for update").setParameter("id", demande.getTitulaireId()).getResultList();
        @SuppressWarnings("unchecked")
        List<Tuple> chevauchements = em.createNativeQuery("""
                select r.nom_complet as remplacant, d.date_debut as dateDebut, d.date_fin as dateFin
                from crm_delegation d join crm_utilisateur r on r.id = d.remplacant_id
                where d.titulaire_id = :titulaireId and d.statut = 'ACTIVE'
                  and d.date_debut <= :dateFin and d.date_fin >= :dateDebut
                """, Tuple.class)
                .setParameter("titulaireId", demande.getTitulaireId())
                .setParameter("dateDebut", demande.getDateDebut())
                .setParameter("dateFin", demande.getDateFin())
                .getResultList();
        if (!chevauchements.isEmpty()) {
            Tuple c = chevauchements.getFirst();
            throw new ErreurMetierException(titulaire + " est déjà remplacé par " + c.get("remplacant", String.class) + " du "
                    + c.get("dateDebut", LocalDate.class).format(DATE_LISIBLE) + " au " + c.get("dateFin", LocalDate.class).format(DATE_LISIBLE) + ".");
        }

        em.createNativeQuery("""
                insert into crm_delegation (titulaire_id, remplacant_id, date_debut, date_fin, motif, autorise_par, created_at)
                values (:titulaireId, :remplacantId, :dateDebut, :dateFin, :motif, :autorisePar, utc_timestamp())
                """)
                .setParameter("titulaireId", demande.getTitulaireId())
                .setParameter("remplacantId", demande.getRemplacantId())
                .setParameter("dateDebut", demande.getDateDebut())
                .setParameter("dateFin", demande.getDateFin())
                .setParameter("motif", demande.getMotif())
                .setParameter("autorisePar", perimetreService.perimetreCourant().getUtilisateurId())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        journalActionService.enregistrer("DELEGATION_CREATION", "crm_delegation", id, remplacant + " remplace " + titulaire + " du "
                + demande.getDateDebut().format(DATE_LISIBLE) + " au " + demande.getDateFin().format(DATE_LISIBLE));
        return consulter(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DelegationDTO> rechercher(DelegationCriteresDTO criteres) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> parametres = new LinkedHashMap<>();
        List<Long> agentsGeres = portefeuilleService.agentsGeres();
        if (agentsGeres != null) {
            // Un agent ne voit que les delegations qui le concernent
            where.append(" and (d.titulaire_id = :moi or d.remplacant_id = :moi)");
            parametres.put("moi", agentsGeres.getFirst());
        } else {
            PerimetreDTO perimetre = perimetreService.perimetreCourant();
            if (!perimetre.isVisionGlobale()) {
                if (perimetre.getAgenceIds().isEmpty()) {
                    return List.of();
                }
                where.append("""
                         and exists (select 1 from crm_utilisateur_agence ua where ua.utilisateur_id = d.titulaire_id
                           and ua.agence_id in (:agencesVisibles) and ua.date_debut <= utc_date()
                           and (ua.date_fin is null or ua.date_fin >= utc_date()))""");
                parametres.put("agencesVisibles", perimetre.getAgenceIds());
            }
        }
        if (criteres.isEnCours()) {
            where.append(" and ").append(DELEGATION_EN_COURS);
        }
        if (criteres.getAgentId() != null) {
            where.append(" and (d.titulaire_id = :agentId or d.remplacant_id = :agentId)");
            parametres.put("agentId", criteres.getAgentId());
        }
        Query requete = em.createNativeQuery(SELECT + where + " order by d.date_debut desc, d.id desc", Tuple.class);
        parametres.forEach(requete::setParameter);
        requete.setMaxResults(500);
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
    }

    @Override
    @Transactional
    public DelegationDTO annuler(Long id) {
        int annulees = em.createNativeQuery("""
                update crm_delegation set statut = 'ANNULEE', annule_par = :utilisateurId, annule_le = utc_timestamp()
                where id = :id and statut = 'ACTIVE'
                """)
                .setParameter("utilisateurId", perimetreService.perimetreCourant().getUtilisateurId())
                .setParameter("id", id)
                .executeUpdate();
        if (annulees == 0) {
            throw new RessourceIntrouvableException("Délégation introuvable");
        }
        journalActionService.enregistrer("DELEGATION_ANNULATION", "crm_delegation", id, null);
        return consulter(id);
    }

    private DelegationDTO consulter(Long id) {
        return mapTupleToDto((Tuple) em.createNativeQuery(SELECT + " where d.id = :id", Tuple.class).setParameter("id", id).getSingleResult());
    }

    private String nomAgent(Long id) {
        @SuppressWarnings("unchecked")
        List<String> noms = em.createNativeQuery("select u.nom_complet from crm_utilisateur u where u.id = :id and " + AGENT_RECOUVREMENT)
                .setParameter("id", id)
                .getResultList();
        if (noms.isEmpty()) {
            throw new ErreurMetierException("La délégation se fait entre deux agents de recouvrement actifs.");
        }
        return noms.getFirst();
    }

    DelegationDTO mapTupleToDto(Tuple tuple) {
        return DelegationDTO.builder()
                .id(tuple.get("id", Long.class))
                .titulaireId(tuple.get("titulaireId", Long.class))
                .titulaireNom(tuple.get("titulaireNom", String.class))
                .titulaireNumero(tuple.get("titulaireNumero", String.class))
                .remplacantId(tuple.get("remplacantId", Long.class))
                .remplacantNom(tuple.get("remplacantNom", String.class))
                .dateDebut(tuple.get("dateDebut", LocalDate.class))
                .dateFin(tuple.get("dateFin", LocalDate.class))
                .motif(tuple.get("motif", String.class))
                .statut(tuple.get("statut", String.class))
                .enCours(((Number) tuple.get("enCours")).intValue() == 1)
                .autorisePar(tuple.get("autorisePar", String.class))
                .build();
    }
}
