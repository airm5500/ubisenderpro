package ci.ubipharm.crm.rest.service.impl;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.SegmentService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.SegmentDTO;
import ci.ubipharm.crm.rest.service.dto.SegmentDemandeDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SegmentServiceImpl implements SegmentService {

    private static final String SELECT = """
            select s.id as id, s.code as code, s.libelle as libelle, s.badge as badge, s.ordre as ordre, s.statut as statut,
                   (select count(*) from crm_client c where c.segmentation = s.code and c.statut <> 'ARCHIVE') as nbClients
            from crm_segment s
            """;

    private final EntityManager em;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;

    @Override
    @Transactional(readOnly = true)
    public List<SegmentDTO> lister(boolean archives) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where s.statut = :statut order by s.ordre, s.code", Tuple.class)
                .setParameter("statut", archives ? "ARCHIVE" : "ACTIF").getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
    }

    @Override
    @Transactional
    public SegmentDTO creer(SegmentDemandeDTO demande) {
        if (demande.getCode() == null || demande.getCode().isBlank()) {
            throw new ErreurMetierException("Saisissez le code du segment, celui des fichiers importés (par exemple P).");
        }
        String code = demande.getCode().trim().toUpperCase(Locale.ROOT);
        verifier(null, code, demande);
        Integer ordre = demande.getOrdre() != null ? demande.getOrdre()
                : ((Number) em.createNativeQuery("select coalesce(max(ordre), 0) + 1 from crm_segment").getSingleResult()).intValue();
        em.createNativeQuery("""
                insert into crm_segment (code, libelle, badge, ordre, statut, modifie_par, created_at)
                values (:code, :libelle, :badge, :ordre, 'ACTIF', :par, utc_timestamp())
                """)
                .setParameter("code", code).setParameter("libelle", demande.getLibelle().trim()).setParameter("badge", demande.getBadge())
                .setParameter("ordre", ordre).setParameter("par", utilisateurCourantService.utilisateur().getId())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        journalActionService.enregistrer("SEGMENT_CREATION", "crm_segment", id, code + " " + demande.getLibelle().trim());
        return consulter(id);
    }

    @Override
    @Transactional
    public SegmentDTO modifier(Long id, SegmentDemandeDTO demande) {
        SegmentDTO avant = consulter(id);
        verifier(id, avant.getCode(), demande);
        em.createNativeQuery("""
                update crm_segment set libelle = :libelle, badge = :badge, ordre = :ordre, modifie_par = :par, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("libelle", demande.getLibelle().trim()).setParameter("badge", demande.getBadge())
                .setParameter("ordre", demande.getOrdre() == null ? avant.getOrdre() : demande.getOrdre())
                .setParameter("par", utilisateurCourantService.utilisateur().getId()).setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("SEGMENT_MODIFICATION", "crm_segment", id, avant.getCode() + " : " + avant.getLibelle() + " ("
                + avant.getBadge() + ") -> " + demande.getLibelle().trim() + " (" + demande.getBadge() + ")");
        return consulter(id);
    }

    @Override
    @Transactional
    public SegmentDTO archiver(Long id) {
        return changerStatut(id, "ARCHIVE");
    }

    @Override
    @Transactional
    public SegmentDTO reactiver(Long id) {
        return changerStatut(id, "ACTIF");
    }

    private SegmentDTO changerStatut(Long id, String statut) {
        SegmentDTO s = consulter(id);
        if (!statut.equals(s.getStatut())) {
            em.createNativeQuery("update crm_segment set statut = :statut, modifie_par = :par, updated_at = utc_timestamp() where id = :id")
                    .setParameter("statut", statut).setParameter("par", utilisateurCourantService.utilisateur().getId()).setParameter("id", id)
                    .executeUpdate();
            journalActionService.enregistrer("ARCHIVE".equals(statut) ? "SEGMENT_ARCHIVAGE" : "SEGMENT_REACTIVATION", "crm_segment", id,
                    s.getCode() + " " + s.getLibelle());
        }
        return consulter(id);
    }

    private void verifier(Long id, String code, SegmentDemandeDTO demande) {
        if (!BADGES.contains(demande.getBadge())) {
            throw new ErreurMetierException("Choisissez un badge parmi ceux proposés.");
        }
        Number doublons = (Number) em.createNativeQuery("select count(*) from crm_segment where id <> :id and (upper(code) = :code or lower(libelle) = lower(:libelle))")
                .setParameter("id", id == null ? 0L : id).setParameter("code", code).setParameter("libelle", demande.getLibelle().trim())
                .getSingleResult();
        if (doublons.longValue() > 0) {
            throw new ErreurMetierException("Un segment porte déjà ce code ou ce nom, peut-être parmi les archivés.");
        }
    }

    private SegmentDTO consulter(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where s.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Segment introuvable");
        }
        return mapTupleToDto(lignes.getFirst());
    }

    SegmentDTO mapTupleToDto(Tuple tuple) {
        return SegmentDTO.builder()
                .id(((Number) tuple.get("id")).longValue())
                .code(tuple.get("code", String.class))
                .libelle(tuple.get("libelle", String.class))
                .badge(tuple.get("badge", String.class))
                .ordre(((Number) tuple.get("ordre")).intValue())
                .statut(tuple.get("statut", String.class))
                .nbClients(((Number) tuple.get("nbClients")).longValue())
                .build();
    }
}
