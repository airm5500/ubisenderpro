package com.ubisenderpro.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.ubisenderpro.entity.Licence;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    /* ----------------- Statuts calculés (grâce, horloge, versions) ----------------- */

    /** Licence correctement signée, sans empreinte ni bornes de version. */
    private Licence licenceSignee() throws Exception {
        Licence l = new Licence();
        l.setPayload(PAYLOAD);
        l.setSignature(signer(PAYLOAD));
        return l;
    }

    private void graceParDefaut() {
        when(parametreService.valeur(eq("licence.grace_jours"), anyString())).thenReturn("7");
    }

    @SuppressWarnings("unchecked")
    private void licenceEnBase(Licence l) {
        TypedQuery<Licence> q = mock(TypedQuery.class);
        when(q.setMaxResults(anyInt())).thenReturn(q);
        when(q.getResultList()).thenReturn(
                l == null ? Collections.emptyList() : Collections.singletonList(l));
        when(em.createQuery(anyString(), eq(Licence.class))).thenReturn(q);
    }

    @Test
    void cycleDeVieDesStatuts() throws Exception {
        graceParDefaut();
        Licence l = licenceSignee();

        l.setDateExpiration(LocalDate.now().plusDays(120));
        assertEquals("ACTIVE", service.statutCalcule(l));

        l.setDateExpiration(LocalDate.now().plusDays(15));
        assertEquals("EXPIRE_BIENTOT", service.statutCalcule(l), "Alerte à 30 jours de l'échéance");

        l.setDateExpiration(LocalDate.now().minusDays(3));
        assertEquals("GRACE", service.statutCalcule(l), "Expirée depuis moins de 7 jours = grâce");

        l.setDateExpiration(LocalDate.now().minusDays(8));
        assertEquals("EXPIREE", service.statutCalcule(l), "Grâce de 7 jours dépassée = expirée");
    }

    @Test
    void reculDHorlogeDetecte() throws Exception {
        graceParDefaut();
        Licence l = licenceSignee();
        l.setDateExpiration(LocalDate.now().plusDays(120));

        l.setDerniereDateVue(LocalDateTime.now().plusHours(48)); // l'horloge a reculé de 2 jours
        assertEquals("HORLOGE", service.statutCalcule(l),
                "Un recul d'horloge net doit être détecté (anti-triche sur l'expiration)");

        l.setDerniereDateVue(LocalDateTime.now().minusHours(1)); // horloge normale
        assertEquals("ACTIVE", service.statutCalcule(l));
    }

    @Test
    void licenceLieeAUnAutreServeurInvalide() throws Exception {
        Licence l = licenceSignee();

        l.setEmpreinteServeur("SRV-AAAA0000BBBB1111"); // un autre serveur
        assertEquals("INVALIDE", service.statutCalcule(l));

        l.setEmpreinteServeur(service.empreinteServeur()); // le bon serveur
        assertEquals("ACTIVE", service.statutCalcule(l));
    }

    @Test
    void versionNonCouverteInvalide() throws Exception {
        Licence l = licenceSignee();

        l.setVersionMin("99.0");
        assertEquals("INVALIDE", service.statutCalcule(l), "Version courante sous la borne minimale");

        l.setVersionMin(null);
        l.setVersionMax("1.0");
        assertEquals("INVALIDE", service.statutCalcule(l), "Version courante au-delà de la borne maximale");

        l.setVersionMax("2.0.0");
        assertEquals("ACTIVE", service.statutCalcule(l), "La version courante 2.0.0 est couverte");
    }

    /* --------------------------- Restrictions --------------------------- */

    @Test
    void modeLibreAucuneRestriction() {
        when(parametreService.valeur(eq("licence.obligatoire"), anyString())).thenReturn("false");
        assertNull(service.modulesAutorises(), "Licence non obligatoire : aucun filtrage de menus");
        assertFalse(service.envoisBloques(), "Licence non obligatoire : envois jamais bloqués");
    }

    @Test
    void licenceObligatoireAbsenteBloqueLesEnvois() {
        when(parametreService.valeur(eq("licence.obligatoire"), anyString())).thenReturn("true");
        licenceEnBase(null);
        assertTrue(service.envoisBloques());
    }

    @Test
    void periodeDeGraceNeBloquePasEncoreLesEnvois() throws Exception {
        when(parametreService.valeur(eq("licence.obligatoire"), anyString())).thenReturn("true");
        graceParDefaut();
        Licence l = licenceSignee();
        licenceEnBase(l);

        l.setDateExpiration(LocalDate.now().minusDays(3)); // en grâce
        assertFalse(service.envoisBloques(), "La grâce laisse le temps de renouveler sans blocage");

        l.setDateExpiration(LocalDate.now().minusDays(30)); // grâce dépassée
        assertTrue(service.envoisBloques());
    }

    @Test
    void modulesAutorisesRespectentLaLicenceEtLeSocle() throws Exception {
        when(parametreService.valeur(eq("licence.obligatoire"), anyString())).thenReturn("true");
        graceParDefaut();
        Licence l = licenceSignee();
        l.setModules("clients,campaigns");
        l.setDateExpiration(LocalDate.now().plusDays(120));
        licenceEnBase(l);

        Set<String> modules = service.modulesAutorises();
        assertNotNull(modules);
        assertTrue(modules.containsAll(Arrays.asList("clients", "campaigns")),
                "Les modules de la licence sont autorisés");
        assertTrue(modules.containsAll(Arrays.asList("dashboard", "settings", "users", "support", "licence")),
                "Le socle (dashboard, paramètres, utilisateurs, support, licence) n'est jamais filtré");
        assertFalse(modules.contains("recouvrement"), "Un module hors licence n'est pas autorisé");
    }
}
