package com.ubisenderpro.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sécurité de la licence : une licence signée avec la clé privée (éditeur) est
 * acceptée ; toute altération de la charge utile ou signature étrangère est
 * refusée. Vérifie aussi la comparaison de versions.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LicenceServiceTest {

    @Mock
    private EntityManager em;
    @Mock
    private ParametreService parametreService;

    @InjectMocks
    private LicenceService service;

    private static final String PAYLOAD =
            "{\"clientId\":\"CLI-001\",\"societe\":\"PHARMA TEST\",\"type\":\"PRO\","
            + "\"dateActivation\":\"2026-01-01\",\"dateExpiration\":\"2027-01-01\","
            + "\"modules\":\"clients,campaigns,waweb\"}";

    /** Signe avec la clé privée DEV du dépôt (même paire que public.pem embarquée). */
    private static String signer(String payload) throws Exception {
        Path pem = Path.of("tools", "ubilicense-manager", "cles", "private_DEV.pem");
        String contenu = Files.readString(pem, StandardCharsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        PrivateKey pk = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(contenu)));
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(pk);
        sig.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign());
    }

    @Test
    void licenceSigneeParLEditeurAcceptee() throws Exception {
        assertTrue(service.signatureValide(PAYLOAD, signer(PAYLOAD)),
                "Une licence signée avec la clé privée de l'éditeur doit être valide");
    }

    @Test
    void chargeUtileAltereeRefusee() throws Exception {
        String signature = signer(PAYLOAD);
        // L'attaquant modifie la date d'expiration sans pouvoir re-signer.
        String falsifie = PAYLOAD.replace("2027-01-01", "2099-01-01");
        assertFalse(service.signatureValide(falsifie, signature),
                "Une charge utile modifiée doit être refusée");
    }

    @Test
    void signatureEtrangereOuCorroympueRefusee() {
        assertFalse(service.signatureValide(PAYLOAD, "AAAA"), "Signature fantaisiste refusée");
        assertFalse(service.signatureValide(PAYLOAD, null), "Signature absente refusée");
        assertFalse(service.signatureValide(null, "AAAA"), "Payload absent refusé");
        assertFalse(service.signatureValide(PAYLOAD, "%%%pas-du-base64%%%"), "Encodage invalide refusé");
    }

    @Test
    void comparaisonDeVersions() {
        assertEquals(0, LicenceService.comparerVersions("2.0.0", "2.0.0"));
        assertTrue(LicenceService.comparerVersions("2.0.0", "1.9.9") > 0);
        assertTrue(LicenceService.comparerVersions("2.0.0", "2.0.1") < 0);
        assertTrue(LicenceService.comparerVersions("2.0", "2.0.0") == 0);
        assertTrue(LicenceService.comparerVersions("10.0.0", "9.0.0") > 0);
    }

    @Test
    void empreinteServeurStableEtFormatee() {
        String e1 = service.empreinteServeur();
        String e2 = service.empreinteServeur();
        assertEquals(e1, e2, "L'empreinte doit être stable sur la même machine");
        assertTrue(e1.startsWith("SRV-"), "Format SRV-XXXX attendu : " + e1);
    }
}
