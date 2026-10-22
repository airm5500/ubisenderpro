package ci.ubipharm.crm.rest.service.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.ChiffrementService;
import lombok.extern.slf4j.Slf4j;

/**
 * AES-256-GCM. La cle est lue dans CRM_CLE_CHIFFREMENT (base64) si elle est fournie, sinon dans le fichier
 * crm.chiffrement.fichier-cle, cree au premier demarrage dans le dossier de travail (dossier donnees du service Windows).
 */
@Slf4j
@Service
public class ChiffrementServiceImpl implements ChiffrementService {

    static final String PREFIXE = "chiffre:v1:";
    private static final int TAILLE_IV = 12;
    private static final int TAILLE_TAG = 128;
    private static final SecureRandom ALEA = new SecureRandom();

    private final NamedParameterJdbcTemplate jdbc;
    private final SecretKeySpec cle;

    public ChiffrementServiceImpl(NamedParameterJdbcTemplate jdbc, @Value("${crm.chiffrement.cle:}") String cleFournie,
            @Value("${crm.chiffrement.fichier-cle:cle-chiffrement.key}") String fichierCle) {
        this.jdbc = jdbc;
        this.cle = new SecretKeySpec(cleFournie.isBlank() ? lireOuCreer(Path.of(fichierCle)) : Base64.getDecoder().decode(cleFournie.trim()), "AES");
    }

    @Override
    public String chiffrer(String clair) {
        if (clair == null || clair.isEmpty() || estChiffre(clair)) {
            return clair;
        }
        try {
            byte[] iv = new byte[TAILLE_IV];
            ALEA.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, cle, new GCMParameterSpec(TAILLE_TAG, iv));
            byte[] chiffre = c.doFinal(clair.getBytes(StandardCharsets.UTF_8));
            return PREFIXE + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + chiffre.length).put(iv).put(chiffre).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Chiffrement impossible", e);
        }
    }

    @Override
    public String dechiffrer(String valeur) {
        if (!estChiffre(valeur)) {
            return valeur;
        }
        try {
            byte[] donnees = Base64.getDecoder().decode(valeur.substring(PREFIXE.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, cle, new GCMParameterSpec(TAILLE_TAG, donnees, 0, TAILLE_IV));
            return new String(c.doFinal(donnees, TAILLE_IV, donnees.length - TAILLE_IV), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Cle changee ou perdue : le secret est a ressaisir, l'envoi echoue comme sans jeton
            log.error("Secret illisible avec la cle de chiffrement actuelle : ressaisissez-le dans Parametres > WhatsApp");
            return null;
        }
    }

    @Override
    public boolean estChiffre(String valeur) {
        return valeur != null && valeur.startsWith(PREFIXE);
    }

    @Override
    @Transactional
    public void chiffrerSecretsEnClair() {
        List<Map<String, Object>> lignes = jdbc.queryForList("select id, jeton_acces, secret_application from crm_compte_meta", Map.of());
        for (Map<String, Object> l : lignes) {
            String jeton = (String) l.get("jeton_acces");
            String secret = (String) l.get("secret_application");
            if ((jeton != null && !jeton.isEmpty() && !estChiffre(jeton)) || (secret != null && !secret.isEmpty() && !estChiffre(secret))) {
                jdbc.update("update crm_compte_meta set jeton_acces = :jeton, secret_application = :secret where id = :id",
                        Map.of("jeton", chiffrerOuVide(jeton), "secret", chiffrerOuVide(secret), "id", l.get("id")));
                log.info("Secrets du compte Meta chiffres en base");
            }
        }
    }

    private String chiffrerOuVide(String valeur) {
        return valeur == null ? "" : chiffrer(valeur);
    }

    private static byte[] lireOuCreer(Path fichier) {
        try {
            if (Files.exists(fichier)) {
                byte[] cle = Base64.getDecoder().decode(Files.readString(fichier).trim());
                if (cle.length != 32) {
                    throw new IllegalStateException("Cle de chiffrement invalide dans " + fichier.toAbsolutePath());
                }
                return cle;
            }
            byte[] cle = new byte[32];
            ALEA.nextBytes(cle);
            Files.writeString(fichier, Base64.getEncoder().encodeToString(cle));
            log.warn("Cle de chiffrement creee : {} (a sauvegarder avec la base)", fichier.toAbsolutePath());
            return cle;
        } catch (IOException e) {
            throw new IllegalStateException("Cle de chiffrement illisible : " + fichier.toAbsolutePath(), e);
        }
    }
}
