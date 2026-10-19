package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.CompteMetaService;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.CompteMetaDTO;
import ci.ubipharm.crm.rest.service.dto.CompteMetaDemandeDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompteMetaServiceImpl implements CompteMetaService {

    static final String CHEMIN_WEBHOOK = "/api/passerelle/meta/webhook";

    private final EntityManager em;
    private final ParametreService parametreService;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;

    @Override
    @Transactional(readOnly = true)
    public CompteMetaDTO consulter() {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select c.jeton_acces is not null and c.jeton_acces <> '' as jetonRenseigne,
                       c.secret_application is not null and c.secret_application <> '' as secretRenseigne,
                       c.jeton_verification as jetonVerification, c.version_api as versionApi, u.nom_complet as modifiePar,
                       coalesce(c.updated_at, c.created_at) as modifieLe
                from crm_compte_meta c left join crm_utilisateur u on u.id = c.modifie_par where c.id = 1
                """, Tuple.class).getResultList();
        Tuple t = lignes.getFirst();
        String base = parametreService.valeur("application.url_publique", "").trim();
        return CompteMetaDTO.builder()
                .jetonRenseigne(((Number) t.get("jetonRenseigne")).intValue() == 1)
                .secretRenseigne(((Number) t.get("secretRenseigne")).intValue() == 1)
                .jetonVerification(t.get("jetonVerification", String.class))
                .urlWebhook(base.isEmpty() ? null : base.replaceAll("/+$", "") + CHEMIN_WEBHOOK)
                .versionApi(t.get("versionApi", String.class))
                .modifiePar(t.get("modifiePar", String.class))
                .modifieLe(t.get("modifieLe", LocalDateTime.class))
                .build();
    }

    @Override
    @Transactional
    public CompteMetaDTO modifier(CompteMetaDemandeDTO demande) {
        String jeton = vide(demande.getJetonAcces());
        String secret = vide(demande.getSecretApplication());
        em.createNativeQuery("""
                update crm_compte_meta set jeton_acces = coalesce(:jeton, jeton_acces), secret_application = coalesce(:secret, secret_application),
                       version_api = :version, modifie_par = :par, updated_at = utc_timestamp()
                where id = 1
                """)
                .setParameter("jeton", jeton).setParameter("secret", secret).setParameter("version", demande.getVersionApi().trim())
                .setParameter("par", utilisateurCourantService.utilisateur().getId())
                .executeUpdate();
        // Les secrets ne vont jamais au journal : seulement ce qui a change
        journalActionService.enregistrer("META_COMPTE_MODIFICATION", "crm_compte_meta", 1L, "version " + demande.getVersionApi().trim()
                + (jeton != null ? ", nouveau jeton d'acces" : "") + (secret != null ? ", nouveau secret d'application" : ""));
        return consulter();
    }

    @Override
    @Transactional
    public CompteMetaDTO renouvelerJetonVerification() {
        em.createNativeQuery("update crm_compte_meta set jeton_verification = :jeton, modifie_par = :par, updated_at = utc_timestamp() where id = 1")
                .setParameter("jeton", UUID.randomUUID().toString().replace("-", ""))
                .setParameter("par", utilisateurCourantService.utilisateur().getId())
                .executeUpdate();
        journalActionService.enregistrer("META_JETON_VERIFICATION", "crm_compte_meta", 1L, "jeton de verification du webhook renouvele");
        return consulter();
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
