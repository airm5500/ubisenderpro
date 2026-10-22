package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.SupportFaqService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.SupportFaqDTO;
import ci.ubipharm.crm.rest.service.dto.SupportFaqDemandeDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SupportFaqServiceImpl implements SupportFaqService {

    private static final String SELECT = """
            select f.id as id, f.categorie as categorie, f.question as question, f.reponse as reponse, f.ordre as ordre, f.statut as statut,
                   u.nom_complet as modifiePar, coalesce(f.updated_at, f.created_at) as modifieLe
            from crm_support_faq f
            left join crm_utilisateur u on u.id = f.modifie_par
            """;

    private final EntityManager em;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;

    @Override
    @Transactional(readOnly = true)
    public List<SupportFaqDTO> lister(boolean archives) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where f.statut = :statut order by f.categorie, f.ordre, f.id", Tuple.class)
                .setParameter("statut", archives ? "ARCHIVE" : "ACTIF").getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
    }

    @Override
    @Transactional
    public SupportFaqDTO creer(SupportFaqDemandeDTO demande) {
        String question = demande.getQuestion().trim();
        verifierDoublon(null, question);
        String categorie = demande.getCategorie().trim().replaceAll("\\s+", " ");
        Integer ordre = demande.getOrdre() != null ? demande.getOrdre()
                : ((Number) em.createNativeQuery("select coalesce(max(ordre), 0) + 1 from crm_support_faq where categorie = :categorie")
                        .setParameter("categorie", categorie).getSingleResult()).intValue();
        em.createNativeQuery("""
                insert into crm_support_faq (categorie, question, reponse, ordre, statut, modifie_par, created_at)
                values (:categorie, :question, :reponse, :ordre, 'ACTIF', :par, utc_timestamp())
                """)
                .setParameter("categorie", categorie).setParameter("question", question).setParameter("reponse", demande.getReponse().trim())
                .setParameter("ordre", ordre).setParameter("par", utilisateurCourantService.utilisateur().getId())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        journalActionService.enregistrer("SUPPORT_FAQ_CREATION", "crm_support_faq", id, categorie + " : " + question);
        return consulter(id);
    }

    @Override
    @Transactional
    public SupportFaqDTO modifier(Long id, SupportFaqDemandeDTO demande) {
        SupportFaqDTO avant = consulter(id);
        String question = demande.getQuestion().trim();
        verifierDoublon(id, question);
        em.createNativeQuery("""
                update crm_support_faq set categorie = :categorie, question = :question, reponse = :reponse, ordre = :ordre, modifie_par = :par,
                       updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("categorie", demande.getCategorie().trim().replaceAll("\\s+", " ")).setParameter("question", question)
                .setParameter("reponse", demande.getReponse().trim())
                .setParameter("ordre", demande.getOrdre() == null ? avant.getOrdre() : demande.getOrdre())
                .setParameter("par", utilisateurCourantService.utilisateur().getId()).setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("SUPPORT_FAQ_MODIFICATION", "crm_support_faq", id,
                avant.getQuestion() + (avant.getQuestion().equals(question) ? "" : " -> " + question));
        return consulter(id);
    }

    @Override
    @Transactional
    public SupportFaqDTO archiver(Long id) {
        return changerStatut(id, "ARCHIVE");
    }

    @Override
    @Transactional
    public SupportFaqDTO reactiver(Long id) {
        return changerStatut(id, "ACTIF");
    }

    private SupportFaqDTO changerStatut(Long id, String statut) {
        SupportFaqDTO f = consulter(id);
        if (statut.equals(f.getStatut())) {
            return f;
        }
        em.createNativeQuery("update crm_support_faq set statut = :statut, modifie_par = :par, updated_at = utc_timestamp() where id = :id")
                .setParameter("statut", statut).setParameter("par", utilisateurCourantService.utilisateur().getId()).setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("ARCHIVE".equals(statut) ? "SUPPORT_FAQ_ARCHIVAGE" : "SUPPORT_FAQ_REACTIVATION", "crm_support_faq", id,
                f.getQuestion());
        return consulter(id);
    }

    private void verifierDoublon(Long id, String question) {
        Number doublons = (Number) em.createNativeQuery("select count(*) from crm_support_faq where id <> :id and lower(question) = lower(:question)")
                .setParameter("id", id == null ? 0L : id).setParameter("question", question).getSingleResult();
        if (doublons.longValue() > 0) {
            throw new ErreurMetierException("Cette question existe déjà dans la FAQ, peut-être parmi les archivées.");
        }
    }

    private SupportFaqDTO consulter(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where f.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Question introuvable");
        }
        return mapTupleToDto(lignes.getFirst());
    }

    SupportFaqDTO mapTupleToDto(Tuple tuple) {
        return SupportFaqDTO.builder()
                .id(((Number) tuple.get("id")).longValue())
                .categorie(tuple.get("categorie", String.class))
                .question(tuple.get("question", String.class))
                .reponse(tuple.get("reponse", String.class))
                .ordre(((Number) tuple.get("ordre")).intValue())
                .statut(tuple.get("statut", String.class))
                .modifiePar(tuple.get("modifiePar", String.class))
                .modifieLe(tuple.get("modifieLe", LocalDateTime.class))
                .build();
    }
}
