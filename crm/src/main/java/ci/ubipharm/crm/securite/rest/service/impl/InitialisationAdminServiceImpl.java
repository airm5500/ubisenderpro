package ci.ubipharm.crm.securite.rest.service.impl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.securite.rest.service.InitialisationAdminService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InitialisationAdminServiceImpl implements InitialisationAdminService {

    private static final Logger LOG = LoggerFactory.getLogger(InitialisationAdminServiceImpl.class);
    private static final String CLE_MOT_DE_PASSE = "admin.mot_de_passe_initial";

    private final EntityManager em;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void initialiserMotDePasseAdmin() {
        @SuppressWarnings("unchecked")
        List<String> valeurs = em.createNativeQuery("select valeur from crm_parametre where cle = :cle")
                .setParameter("cle", CLE_MOT_DE_PASSE)
                .getResultList();
        String motDePasse = valeurs.isEmpty() ? null : valeurs.getFirst();
        if (motDePasse == null || motDePasse.isBlank()) {
            return;
        }
        int modifies = em.createNativeQuery("""
                update crm_utilisateur set mot_de_passe_hash = :hash, updated_at = utc_timestamp()
                where login = 'admin' and mot_de_passe_hash = 'INIT'
                """)
                .setParameter("hash", passwordEncoder.encode(motDePasse))
                .executeUpdate();
        // Le mot de passe en clair ne reste pas en base une fois utilise
        em.createNativeQuery("update crm_parametre set valeur = null, updated_at = utc_timestamp() where cle = :cle")
                .setParameter("cle", CLE_MOT_DE_PASSE)
                .executeUpdate();
        if (modifies > 0) {
            LOG.info("Mot de passe initial du compte admin enregistre");
        }
    }
}
