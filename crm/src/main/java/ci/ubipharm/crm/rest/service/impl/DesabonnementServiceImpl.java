package ci.ubipharm.crm.rest.service.impl;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.DesabonnementService;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DesabonnementServiceImpl implements DesabonnementService {

    static final String MOTS_PAR_DEFAUT = "STOP, ARRET, DESABONNER";

    private final EntityManager em;
    private final PerimetreService perimetreService;
    private final ParametreService parametreService;
    private final JournalActionService journalActionService;

    @Override
    @Transactional
    public void desabonner(Long clientId, String motif) {
        String code = clientDuPerimetre(clientId);
        String texte = motif == null || motif.isBlank() ? "Désabonné à la main" : motif.trim();
        int n = em.createNativeQuery("""
                update crm_client set desabonne_marketing_le = utc_timestamp(), desabonne_marketing_motif = :motif, updated_at = utc_timestamp()
                where id = :id and desabonne_marketing_le is null
                """).setParameter("motif", texte).setParameter("id", clientId).executeUpdate();
        if (n > 0) {
            journalActionService.enregistrer("CLIENT_DESABONNEMENT", "crm_client", clientId, code + " : " + texte);
        }
    }

    @Override
    @Transactional
    public void reabonner(Long clientId) {
        String code = clientDuPerimetre(clientId);
        int n = em.createNativeQuery("""
                update crm_client set desabonne_marketing_le = null, desabonne_marketing_motif = null, updated_at = utc_timestamp()
                where id = :id and desabonne_marketing_le is not null
                """).setParameter("id", clientId).executeUpdate();
        if (n > 0) {
            journalActionService.enregistrer("CLIENT_REABONNEMENT", "crm_client", clientId, code);
        }
    }

    @Override
    @Transactional
    public boolean traiterReponse(Long clientId, String texte) {
        if (clientId == null || texte == null || texte.isBlank()) {
            return false;
        }
        String recu = normaliser(texte);
        List<String> mots = Arrays.stream(parametreService.valeur("marketing.mots_desabonnement", MOTS_PAR_DEFAUT).split(","))
                .map(DesabonnementServiceImpl::normaliser).filter(m -> !m.isEmpty()).toList();
        if (!mots.contains(recu)) {
            return false;
        }
        int n = em.createNativeQuery("""
                update crm_client set desabonne_marketing_le = utc_timestamp(), desabonne_marketing_motif = :motif, updated_at = utc_timestamp()
                where id = :id and desabonne_marketing_le is null
                """).setParameter("motif", "Réponse WhatsApp « " + texte.trim() + " »").setParameter("id", clientId).executeUpdate();
        if (n > 0) {
            journalActionService.enregistrerSysteme("CLIENT_DESABONNEMENT", "crm_client", clientId, "Réponse WhatsApp « " + texte.trim() + " »");
        }
        return n > 0;
    }

    /** « Stop ! », « arrêt. » et « STOP » se valent. */
    static String normaliser(String texte) {
        String sansAccents = Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sansAccents.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", " ").trim().replaceAll("\\s+", " ");
    }

    private String clientDuPerimetre(Long clientId) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("select code as code, agence_id as agenceId from crm_client where id = :id", Tuple.class)
                .setParameter("id", clientId).getResultList();
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (lignes.isEmpty() || !perimetre.isVisionGlobale() && !perimetre.getAgenceIds().contains(lignes.getFirst().get("agenceId", Long.class))) {
            throw new RessourceIntrouvableException("Client introuvable");
        }
        return lignes.getFirst().get("code", String.class);
    }
}
