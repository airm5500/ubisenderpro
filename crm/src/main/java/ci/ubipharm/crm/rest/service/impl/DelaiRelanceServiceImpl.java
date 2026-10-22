package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.DelaiRelanceService;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.DelaiRelanceDTO;
import ci.ubipharm.crm.rest.service.dto.DelaiRelanceDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DelaiRelanceServiceImpl implements DelaiRelanceService {

    static final String AVANT_MAX = "campagne.avant_echeance_jours_max";
    static final String AVANT_MIN = "campagne.avant_echeance_jours_min";
    static final String RETARD_MIN = "campagne.apres_echeance_jours_min";
    static final String INTERVALLE = "campagne.relance_intervalle_jours";

    private static final String SELECT = """
            select d.id as id, d.agence_id as agenceId, a.libelle as agenceLibelle, d.segment as segment, s.libelle as segmentLibelle,
                   d.avant_jours_max as avantJoursMax, d.avant_jours_min as avantJoursMin, d.retard_jours_min as retardJoursMin,
                   d.intervalle_jours as intervalleJours, d.statut as statut, u.nom_complet as modifiePar,
                   coalesce(d.updated_at, d.created_at) as modifieLe
            from crm_delai_relance d
            left join crm_agence a on a.id = d.agence_id
            left join crm_segment s on s.code = d.segment
            left join crm_utilisateur u on u.id = d.modifie_par
            """;

    private final EntityManager em;
    private final ParametreService parametreService;
    private final PerimetreService perimetreService;
    private final JournalActionService journalActionService;
    private final UtilisateurCourantService utilisateurCourantService;

    @Override
    @Transactional(readOnly = true)
    public DelaiRelanceDTO defauts() {
        return DelaiRelanceDTO.builder()
                .avantJoursMax(parametreService.entier(AVANT_MAX, 5))
                .avantJoursMin(parametreService.entier(AVANT_MIN, 1))
                .retardJoursMin(parametreService.entier(RETARD_MIN, 1))
                .intervalleJours(parametreService.entier(INTERVALLE, 0))
                .statut("ACTIF")
                .modifiable(perimetreService.perimetreCourant().isVisionGlobale())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DelaiRelanceDTO> lister(boolean archives) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        StringBuilder sql = new StringBuilder(SELECT).append(" where d.statut = :statut");
        if (!perimetre.isVisionGlobale()) {
            sql.append(perimetre.getAgenceIds().isEmpty() ? " and d.agence_id is null" : " and (d.agence_id is null or d.agence_id in (:agences))");
        }
        sql.append(" order by a.libelle is not null, a.libelle, s.ordre is not null, s.ordre");
        Query requete = em.createNativeQuery(sql.toString(), Tuple.class).setParameter("statut", archives ? "ARCHIVE" : "ACTIF");
        if (!perimetre.isVisionGlobale() && !perimetre.getAgenceIds().isEmpty()) {
            requete.setParameter("agences", perimetre.getAgenceIds());
        }
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return lignes.stream().map(t -> mapTupleToDto(t, perimetre)).toList();
    }

    @Override
    @Transactional
    public DelaiRelanceDTO creer(DelaiRelanceDemandeDTO demande) {
        String segment = vide(demande.getSegment());
        verifier(demande, segment, null);
        em.createNativeQuery("""
                insert into crm_delai_relance (agence_id, segment, avant_jours_max, avant_jours_min, retard_jours_min, intervalle_jours,
                    modifie_par, created_at)
                values (:agence, :segment, :avantMax, :avantMin, :retardMin, :intervalle, :par, utc_timestamp())
                """)
                .setParameter("agence", demande.getAgenceId()).setParameter("segment", segment)
                .setParameter("avantMax", demande.getAvantJoursMax()).setParameter("avantMin", demande.getAvantJoursMin())
                .setParameter("retardMin", demande.getRetardJoursMin()).setParameter("intervalle", demande.getIntervalleJours())
                .setParameter("par", utilisateurCourantService.utilisateur().getId())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        DelaiRelanceDTO cree = consulter(id);
        journalActionService.enregistrer("DELAI_RELANCE_CREATION", "crm_delai_relance", id, cible(cree) + " : " + valeurs(cree));
        return cree;
    }

    @Override
    @Transactional
    public DelaiRelanceDTO modifier(Long id, DelaiRelanceDemandeDTO demande) {
        DelaiRelanceDTO actuel = consulterModifiable(id);
        if ("ARCHIVE".equals(actuel.getStatut())) {
            throw new ErreurMetierException("Réactivez d'abord cette règle pour la modifier.");
        }
        // L'agence et le segment d'une regle ne changent pas : on archive et on en cree une autre
        demande.setAgenceId(actuel.getAgenceId());
        verifier(demande, actuel.getSegment(), id);
        em.createNativeQuery("""
                update crm_delai_relance set avant_jours_max = :avantMax, avant_jours_min = :avantMin, retard_jours_min = :retardMin,
                    intervalle_jours = :intervalle, modifie_par = :par, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("avantMax", demande.getAvantJoursMax()).setParameter("avantMin", demande.getAvantJoursMin())
                .setParameter("retardMin", demande.getRetardJoursMin()).setParameter("intervalle", demande.getIntervalleJours())
                .setParameter("par", utilisateurCourantService.utilisateur().getId()).setParameter("id", id)
                .executeUpdate();
        DelaiRelanceDTO modifie = consulter(id);
        journalActionService.enregistrer("DELAI_RELANCE_MODIFICATION", "crm_delai_relance", id,
                cible(modifie) + " : " + valeurs(actuel) + " -> " + valeurs(modifie));
        return modifie;
    }

    @Override
    @Transactional
    public DelaiRelanceDTO archiver(Long id) {
        DelaiRelanceDTO actuel = consulterModifiable(id);
        changerStatut(id, "ARCHIVE");
        journalActionService.enregistrer("DELAI_RELANCE_ARCHIVAGE", "crm_delai_relance", id, cible(actuel));
        return consulter(id);
    }

    @Override
    @Transactional
    public DelaiRelanceDTO reactiver(Long id) {
        DelaiRelanceDTO actuel = consulterModifiable(id);
        if (doublon(actuel.getAgenceId(), actuel.getSegment(), id)) {
            throw new ErreurMetierException("Une règle active existe déjà pour " + cible(actuel) + " : modifiez-la plutôt.");
        }
        changerStatut(id, "ACTIF");
        journalActionService.enregistrer("DELAI_RELANCE_REACTIVATION", "crm_delai_relance", id, cible(actuel));
        return consulter(id);
    }

    private void verifier(DelaiRelanceDemandeDTO demande, String segment, Long id) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (demande.getAgenceId() == null && !perimetre.isVisionGlobale()) {
            throw new ErreurMetierException("Seule la Direction du Recouvrement règle les délais de toutes les agences ; choisissez votre agence.");
        }
        if (demande.getAgenceId() != null && !perimetre.isVisionGlobale() && !perimetre.getAgenceIds().contains(demande.getAgenceId())) {
            throw new ErreurMetierException("Cette agence ne fait pas partie de votre périmètre.");
        }
        if (demande.getAgenceId() != null && ((Number) em.createNativeQuery("select count(*) from crm_agence where id = :id")
                .setParameter("id", demande.getAgenceId()).getSingleResult()).longValue() == 0) {
            throw new ErreurMetierException("Agence inconnue.");
        }
        if (segment != null && ((Number) em.createNativeQuery("select count(*) from crm_segment where code = :code and statut = 'ACTIF'")
                .setParameter("code", segment).getSingleResult()).longValue() == 0) {
            throw new ErreurMetierException("Segment inconnu : " + segment + ".");
        }
        if (demande.getAgenceId() == null && segment == null) {
            throw new ErreurMetierException("Pour toutes les agences et tous les segments, modifiez les délais par défaut dans les paramètres.");
        }
        if (demande.getAvantJoursMax() == null && demande.getAvantJoursMin() == null && demande.getRetardJoursMin() == null
                && demande.getIntervalleJours() == null) {
            throw new ErreurMetierException("Renseignez au moins un délai.");
        }
        int avantMax = demande.getAvantJoursMax() != null ? demande.getAvantJoursMax() : parametreService.entier(AVANT_MAX, 5);
        int avantMin = demande.getAvantJoursMin() != null ? demande.getAvantJoursMin() : parametreService.entier(AVANT_MIN, 1);
        if ((demande.getAvantJoursMax() != null || demande.getAvantJoursMin() != null) && avantMin > avantMax) {
            throw new ErreurMetierException("Avant échéance : le dernier jour (J-" + avantMin + ") doit être plus proche de l'échéance que le premier (J-"
                    + avantMax + ").");
        }
        if (doublon(demande.getAgenceId(), segment, id)) {
            throw new ErreurMetierException("Une règle existe déjà pour cette agence et ce segment : modifiez-la plutôt.");
        }
    }

    private boolean doublon(Long agenceId, String segment, Long id) {
        return ((Number) em.createNativeQuery("""
                select count(*) from crm_delai_relance where statut = 'ACTIF' and id <> :id
                and agence_id <=> :agence and segment <=> :segment
                """).setParameter("id", id == null ? 0L : id).setParameter("agence", agenceId).setParameter("segment", segment)
                .getSingleResult()).longValue() > 0;
    }

    private DelaiRelanceDTO consulter(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where d.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Règle de délais introuvable.");
        }
        return mapTupleToDto(lignes.getFirst(), perimetreService.perimetreCourant());
    }

    private DelaiRelanceDTO consulterModifiable(Long id) {
        DelaiRelanceDTO regle = consulter(id);
        if (!regle.isModifiable()) {
            throw new ErreurMetierException("Seule la Direction du Recouvrement modifie les règles communes à toutes les agences.");
        }
        return regle;
    }

    private void changerStatut(Long id, String statut) {
        em.createNativeQuery("update crm_delai_relance set statut = :statut, modifie_par = :par, updated_at = utc_timestamp() where id = :id")
                .setParameter("statut", statut).setParameter("par", utilisateurCourantService.utilisateur().getId()).setParameter("id", id)
                .executeUpdate();
    }

    private static String cible(DelaiRelanceDTO d) {
        return (d.getAgenceLibelle() == null ? "toutes agences" : d.getAgenceLibelle()) + ", "
                + (d.getSegmentLibelle() == null ? "tous segments" : d.getSegmentLibelle());
    }

    private static String valeurs(DelaiRelanceDTO d) {
        return "avant J-" + texte(d.getAvantJoursMax()) + " a J-" + texte(d.getAvantJoursMin()) + ", apres J+" + texte(d.getRetardJoursMin())
                + ", intervalle " + texte(d.getIntervalleJours());
    }

    private static String texte(Integer valeur) {
        return valeur == null ? "(defaut)" : valeur.toString();
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    DelaiRelanceDTO mapTupleToDto(Tuple tuple, PerimetreDTO perimetre) {
        Long agenceId = tuple.get("agenceId", Long.class);
        return DelaiRelanceDTO.builder()
                .id(tuple.get("id", Long.class))
                .agenceId(agenceId)
                .agenceLibelle(tuple.get("agenceLibelle", String.class))
                .segment(tuple.get("segment", String.class))
                .segmentLibelle(tuple.get("segmentLibelle", String.class))
                .avantJoursMax(tuple.get("avantJoursMax", Integer.class))
                .avantJoursMin(tuple.get("avantJoursMin", Integer.class))
                .retardJoursMin(tuple.get("retardJoursMin", Integer.class))
                .intervalleJours(tuple.get("intervalleJours", Integer.class))
                .statut(tuple.get("statut", String.class))
                .modifiable(perimetre.isVisionGlobale() || agenceId != null && perimetre.getAgenceIds().contains(agenceId))
                .modifiePar(tuple.get("modifiePar", String.class))
                .modifieLe(tuple.get("modifieLe", LocalDateTime.class))
                .build();
    }
}
