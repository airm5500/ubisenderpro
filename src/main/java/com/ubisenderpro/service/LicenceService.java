package com.ubisenderpro.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubisenderpro.entity.Licence;
import com.ubisenderpro.entity.LicenceEvenement;
import com.ubisenderpro.rest.AboutResource;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Client de licence UbiSmartCRM Pro.
 *
 * <p>La licence est une charge utile JSON <b>signée</b> (RSA/SHA-256) par
 * l'éditeur (UbiLicense Manager, clé privée hors produit). L'application ne
 * fait que <b>vérifier</b> la signature avec la clé publique embarquée
 * ({@code /licence/public.pem}) : infalsifiable sans la clé privée.</p>
 *
 * <p>Format d'une clé/fichier {@code .lic} :
 * {@code base64url(payloadJson) + "." + base64url(signature)}.</p>
 *
 * <p>Sans licence ou si {@code licence.obligatoire=false} : aucune restriction
 * (bandeau d'information seulement) — zéro régression pour l'existant.</p>
 */
@Stateless
public class LicenceService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Menus « socle » jamais filtrés par la licence. */
    private static final Set<String> MENUS_SOCLE = new HashSet<>(Arrays.asList(
            "dashboard", "settings", "users", "support", "licence"));

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private ParametreService parametreService;

    /* ------------------------------- État ------------------------------- */

    /** Licence courante (dernière importée) ou null. */
    public Licence courante() {
        List<Licence> l = em.createQuery(
                "SELECT l FROM Licence l ORDER BY l.importeeLe DESC", Licence.class)
                .setMaxResults(1).getResultList();
        return l.isEmpty() ? null : l.get(0);
    }

    /**
     * État consolidé : statut calculé + jours restants + alertes.
     * Statuts : AUCUNE, ACTIVE, EXPIRE_BIENTOT, GRACE, EXPIREE, INVALIDE, HORLOGE.
     */
    public Map<String, Object> etat() {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean obligatoire = "true".equalsIgnoreCase(parametreService.valeur("licence.obligatoire", "false"));
        m.put("obligatoire", obligatoire);
        m.put("empreinteServeur", empreinteServeur());
        m.put("version", AboutResource.VERSION);

        Licence l = courante();
        if (l == null) {
            m.put("statut", "AUCUNE");
            m.put("message", obligatoire
                    ? "Aucune licence installée : fonctions d'envoi désactivées."
                    : "Aucune licence installée (mode libre).");
            return m;
        }
        String statut = statutCalcule(l);
        m.put("statut", statut);
        m.put("societe", l.getSociete());
        m.put("clientId", l.getClientId());
        m.put("type", l.getType());
        m.put("dateActivation", String.valueOf(l.getDateActivation()));
        m.put("dateExpiration", String.valueOf(l.getDateExpiration()));
        m.put("maxUsers", l.getMaxUsers());
        m.put("maxAgences", l.getMaxAgences());
        m.put("modules", l.getModules());
        if (l.getDateExpiration() != null) {
            long jours = ChronoUnit.DAYS.between(LocalDate.now(), l.getDateExpiration());
            m.put("joursRestants", jours);
        }
        m.put("bloqueEnvois", envoisBloques());
        switch (statut) {
            case "INVALIDE": m.put("message", "Licence invalide (signature ou serveur non conforme)."); break;
            case "HORLOGE": m.put("message", "Recul d'horloge détecté : fonctions restreintes jusqu'à vérification."); break;
            case "EXPIREE": m.put("message", "Licence expirée : renouvelez-la depuis cet écran."); break;
            case "GRACE": m.put("message", "Licence expirée — période de grâce en cours."); break;
            case "EXPIRE_BIENTOT": m.put("message", "La licence expire bientôt : pensez au renouvellement."); break;
            default: m.put("message", "Licence active."); break;
        }
        return m;
    }

    /** Statut calculé à l'instant T (vérifie signature, empreinte, horloge, dates). */
    public String statutCalcule(Licence l) {
        if (l == null) { return "AUCUNE"; }
        if (!signatureValide(l.getPayload(), l.getSignature())) { return "INVALIDE"; }
        if (l.getEmpreinteServeur() != null && !l.getEmpreinteServeur().isEmpty()
                && !l.getEmpreinteServeur().equals(empreinteServeur())) { return "INVALIDE"; }
        if (!versionCouverte(l)) { return "INVALIDE"; }
        // Anti-recul d'horloge : si l'horloge est revenue nettement en arrière.
        if (l.getDerniereDateVue() != null
                && LocalDateTime.now().isBefore(l.getDerniereDateVue().minusHours(24))) {
            return "HORLOGE";
        }
        if (l.getDateExpiration() != null) {
            LocalDate auj = LocalDate.now();
            if (auj.isAfter(l.getDateExpiration())) {
                int grace = entier(parametreService.valeur("licence.grace_jours", "7"), 7);
                return auj.isAfter(l.getDateExpiration().plusDays(grace)) ? "EXPIREE" : "GRACE";
            }
            if (!auj.isBefore(l.getDateExpiration().minusDays(30))) { return "EXPIRE_BIENTOT"; }
        }
        return "ACTIVE";
    }

    /* ------------------------------ Import ------------------------------ */

    /** Importe une clé d'activation / un fichier .lic (contenu texte). */
    public Licence importer(String contenu, String utilisateur) {
        if (contenu == null || contenu.trim().isEmpty()) {
            throw new ValidationException("contenu", "Collez la clé d'activation ou le contenu du fichier .lic.");
        }
        String brut = contenu.trim().replaceAll("\\s+", "");
        int sep = brut.lastIndexOf('.');
        if (sep <= 0) {
            throw new ValidationException("contenu", "Format de licence invalide (payload.signature attendu).");
        }
        String payloadB64 = brut.substring(0, sep);
        String signatureB64 = brut.substring(sep + 1);
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("contenu", "Encodage de la licence illisible.");
        }
        if (!signatureValide(payload, signatureB64)) {
            tracer("REFUS", "Signature invalide", utilisateur);
            throw new ValidationException("contenu",
                    "Signature invalide : cette licence n'a pas été émise par l'éditeur.");
        }

        JsonNode n;
        try { n = MAPPER.readTree(payload); }
        catch (Exception e) { throw new ValidationException("contenu", "Contenu de licence illisible."); }

        // Empreinte serveur (tolérante : contrôlée seulement si présente dans la licence).
        String emp = texte(n, "empreinteServeur");
        if (emp != null && !emp.isEmpty() && !emp.equals(empreinteServeur())) {
            tracer("REFUS", "Empreinte serveur non conforme", utilisateur);
            throw new ValidationException("contenu",
                    "Cette licence est liée à un autre serveur (empreinte attendue : " + emp
                    + ", ce serveur : " + empreinteServeur()
                    + "). Demandez un transfert à l'éditeur.");
        }

        Licence l = courante();
        boolean renouvellement = l != null;
        if (l == null) { l = new Licence(); }
        l.setClientId(texte(n, "clientId"));
        l.setSociete(texte(n, "societe"));
        l.setPays(texte(n, "pays"));
        l.setEmail(texte(n, "email"));
        l.setType(texte(n, "type"));
        l.setDateActivation(date(n, "dateActivation"));
        l.setDateExpiration(date(n, "dateExpiration"));
        l.setMaxUsers(entierOuNull(n, "maxUsers"));
        l.setMaxAgences(entierOuNull(n, "maxAgences"));
        l.setModules(texte(n, "modules"));
        l.setVersionMin(texte(n, "versionMin"));
        l.setVersionMax(texte(n, "versionMax"));
        l.setEmpreinteServeur(emp);
        l.setSignature(signatureB64);
        l.setPayload(payload);
        l.setStatut("ACTIVE");
        l.setDerniereDateVue(LocalDateTime.now());
        l.setImporteeLe(LocalDateTime.now());
        if (!versionCouverte(l)) {
            tracer("REFUS", "Version non couverte (" + AboutResource.VERSION + ")", utilisateur);
            throw new ValidationException("contenu",
                    "Cette licence ne couvre pas la version " + AboutResource.VERSION + " de l'application.");
        }
        if (renouvellement) { em.merge(l); } else { em.persist(l); }
        tracer(renouvellement ? "RENOUVELLEMENT" : "ACTIVATION",
                l.getSociete() + " — " + l.getType() + " → " + l.getDateExpiration(), utilisateur);
        return l;
    }

    /** Demande d'activation hors ligne (.licreq) : identité + empreinte serveur. */
    public Map<String, Object> genererDemande(String societe, String email, String utilisateur) {
        Map<String, Object> demande = new LinkedHashMap<>();
        demande.put("societe", societe);
        demande.put("email", email);
        demande.put("empreinteServeur", empreinteServeur());
        demande.put("version", AboutResource.VERSION);
        demande.put("dateDemande", LocalDate.now().toString());
        String json;
        try { json = MAPPER.writeValueAsString(demande); }
        catch (Exception e) { throw new ValidationException(null, "Génération de la demande impossible."); }
        String contenu = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        tracer("DEMANDE", "Demande d'activation générée", utilisateur);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fichier", "REQUEST.licreq");
        out.put("contenu", contenu);
        out.put("empreinteServeur", empreinteServeur());
        return out;
    }

    /* --------------------------- Restrictions --------------------------- */

    /**
     * Modules autorisés par la licence, ou null si aucune restriction
     * (licence non obligatoire, absente, ou sans liste de modules).
     */
    public Set<String> modulesAutorises() {
        if (!"true".equalsIgnoreCase(parametreService.valeur("licence.obligatoire", "false"))) { return null; }
        Licence l = courante();
        if (l == null) { return null; } // pas de licence : géré par envoisBloques()
        String statut = statutCalcule(l);
        if ("INVALIDE".equals(statut)) { return null; }
        if (l.getModules() == null || l.getModules().trim().isEmpty()) { return null; }
        Set<String> out = new HashSet<>(MENUS_SOCLE);
        for (String mdl : l.getModules().split(",")) {
            if (!mdl.trim().isEmpty()) { out.add(mdl.trim().toLowerCase()); }
        }
        return out;
    }

    /**
     * Vrai si les fonctions « sous licence » (envois, automatisations, imports
     * de masse) doivent être bloquées : licence obligatoire ET (absente,
     * invalide, expirée au-delà de la grâce, ou horloge suspecte).
     */
    public boolean envoisBloques() {
        if (!"true".equalsIgnoreCase(parametreService.valeur("licence.obligatoire", "false"))) { return false; }
        Licence l = courante();
        if (l == null) { return true; }
        String s = statutCalcule(l);
        return "EXPIREE".equals(s) || "INVALIDE".equals(s) || "HORLOGE".equals(s);
    }

    /** Battement : mémorise la plus grande date vue (anti-recul d'horloge). */
    public void battement() {
        try {
            Licence l = courante();
            if (l == null) { return; }
            LocalDateTime now = LocalDateTime.now();
            if (l.getDerniereDateVue() == null || now.isAfter(l.getDerniereDateVue())) {
                l.setDerniereDateVue(now);
                em.merge(l);
            }
        } catch (RuntimeException ignore) { /* jamais bloquant */ }
    }

    public List<LicenceEvenement> evenements(int limit) {
        return em.createQuery("SELECT e FROM LicenceEvenement e ORDER BY e.createdAt DESC", LicenceEvenement.class)
                .setMaxResults(limit > 0 && limit <= 500 ? limit : 100).getResultList();
    }

    /* ------------------------------ interne ------------------------------ */

    /** Empreinte serveur tolérante : nom d'hôte + adresse MAC principale (hash court). */
    public String empreinteServeur() {
        StringBuilder base = new StringBuilder();
        try { base.append(InetAddress.getLocalHost().getHostName()); } catch (Exception ignore) { }
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                byte[] mac = ni.getHardwareAddress();
                if (mac != null && !ni.isLoopback() && !ni.isVirtual()) {
                    for (byte b : mac) { base.append(String.format("%02x", b)); }
                    break;
                }
            }
        } catch (Exception ignore) { }
        if (base.length() == 0) { base.append("inconnu"); }
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(base.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("SRV-");
            for (int i = 0; i < 8; i++) { sb.append(String.format("%02X", h[i])); }
            return sb.toString();
        } catch (Exception e) {
            return "SRV-" + Integer.toHexString(base.toString().hashCode()).toUpperCase();
        }
    }

    /** Vérifie la signature RSA/SHA-256 avec la clé publique embarquée. */
    boolean signatureValide(String payload, String signatureB64) {
        if (payload == null || signatureB64 == null) { return false; }
        try {
            PublicKey pk = clePublique();
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(pk);
            sig.update(payload.getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getUrlDecoder().decode(signatureB64));
        } catch (Exception e) {
            return false;
        }
    }

    private PublicKey clePublique() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/licence/public.pem")) {
            if (in == null) { throw new IllegalStateException("Clé publique absente du livrable."); }
            String pem = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(pem);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        }
    }

    private boolean versionCouverte(Licence l) {
        String v = AboutResource.VERSION;
        if (l.getVersionMin() != null && !l.getVersionMin().isEmpty()
                && comparerVersions(v, l.getVersionMin()) < 0) { return false; }
        if (l.getVersionMax() != null && !l.getVersionMax().isEmpty()
                && comparerVersions(v, l.getVersionMax()) > 0) { return false; }
        return true;
    }

    static int comparerVersions(String a, String b) {
        String[] pa = a.split("\\."), pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int na = i < pa.length ? entier(pa[i].replaceAll("[^0-9]", ""), 0) : 0;
            int nb = i < pb.length ? entier(pb[i].replaceAll("[^0-9]", ""), 0) : 0;
            if (na != nb) { return Integer.compare(na, nb); }
        }
        return 0;
    }

    private void tracer(String type, String detail, String utilisateur) {
        try {
            LicenceEvenement e = new LicenceEvenement();
            e.setType(type);
            e.setDetail(detail != null && detail.length() > 500 ? detail.substring(0, 500) : detail);
            e.setUtilisateur(utilisateur);
            em.persist(e);
        } catch (RuntimeException ignore) { }
    }

    private static String texte(JsonNode n, String champ) {
        JsonNode v = n.get(champ);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Integer entierOuNull(JsonNode n, String champ) {
        JsonNode v = n.get(champ);
        return v == null || v.isNull() || !v.canConvertToInt() ? null : v.asInt();
    }

    private static LocalDate date(JsonNode n, String champ) {
        String s = texte(n, champ);
        if (s == null || s.isEmpty()) { return null; }
        try { return LocalDate.parse(s.substring(0, 10)); } catch (RuntimeException e) { return null; }
    }

    private static int entier(String s, int defaut) {
        try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return defaut; }
    }
}
