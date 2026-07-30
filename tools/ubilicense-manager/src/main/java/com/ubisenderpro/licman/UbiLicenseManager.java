package com.ubisenderpro.licman;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UbiLicense Manager — outil ÉDITEUR (bureau, Swing) de gestion des licences
 * UbiSmartCRM Pro. Il détient la CLÉ PRIVÉE : ne jamais le livrer au client.
 *
 * <ul>
 *   <li>Onglet « Clés » : génération de la paire RSA 2048 (private.pem /
 *       public.pem). La clé publique est à embarquer dans l'application
 *       (src/main/resources/licence/public.pem) avant build du WAR.</li>
 *   <li>Onglet « Licence » : formulaire client + dates + modules + empreinte
 *       (chargée depuis un fichier .licreq du client) → génère le fichier
 *       {@code LICENSE.lic} signé et la clé d'activation équivalente.</li>
 * </ul>
 *
 * Format : {@code base64url(payloadJson) + "." + base64url(signature RSA/SHA-256)}.
 */
public final class UbiLicenseManager extends JFrame {

    private static final String[] MODULES = {
            "inbox", "clients", "catalogue", "promotions", "marketing", "dispo",
            "infos", "campaigns", "waweb", "historique", "crm", "recouvrement"
    };
    private static final String[] TYPES = {"ESSAI", "STANDARD", "PRO", "ENTREPRISE"};

    private final JTextField cheminClePrivee = new JTextField("cles/private.pem", 28);
    private final JTextField fClientId = new JTextField("CLI-0001", 16);
    private final JTextField fSociete = new JTextField(22);
    private final JTextField fPays = new JTextField("Côte d'Ivoire", 16);
    private final JTextField fEmail = new JTextField(22);
    private final JComboBox<String> fType = new JComboBox<>(TYPES);
    private final JTextField fActivation = new JTextField(LocalDate.now().toString(), 10);
    private final JTextField fExpiration = new JTextField(LocalDate.now().plusYears(1).toString(), 10);
    private final JTextField fMaxUsers = new JTextField("10", 5);
    private final JTextField fMaxAgences = new JTextField("5", 5);
    private final JTextField fVersionMin = new JTextField("2.0.0", 8);
    private final JTextField fVersionMax = new JTextField(8);
    private final JTextField fEmpreinte = new JTextField(20);
    private final JCheckBox[] cModules = new JCheckBox[MODULES.length];
    private final JTextArea sortie = new JTextArea(7, 60);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new UbiLicenseManager().setVisible(true));
    }

    private UbiLicenseManager() {
        super("UbiLicense Manager — éditeur UbiSmartCRM Pro");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JTabbedPane onglets = new JTabbedPane();
        onglets.addTab("🔑 Licence", ongletLicence());
        onglets.addTab("🗝️ Clés", ongletCles());
        add(onglets);
        setSize(860, 640);
        setLocationRelativeTo(null);
    }

    /* ------------------------------ Onglet Licence ------------------------------ */

    private JPanel ongletLicence() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(10, 12, 6, 12));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 4, 3, 4);
        g.anchor = GridBagConstraints.WEST;
        int y = 0;
        y = ligne(form, g, y, "Clé privée (.pem) :", ligneAvecBouton(cheminClePrivee, "…", this::choisirClePrivee));
        y = ligne(form, g, y, "Identifiant client :", fClientId);
        y = ligne(form, g, y, "Société :", fSociete);
        y = ligne(form, g, y, "Pays :", fPays);
        y = ligne(form, g, y, "E-mail :", fEmail);
        y = ligne(form, g, y, "Type :", fType);
        y = ligne(form, g, y, "Activation (AAAA-MM-JJ) :", fActivation);
        y = ligne(form, g, y, "Expiration (AAAA-MM-JJ) :", fExpiration);
        y = ligne(form, g, y, "Utilisateurs max :", fMaxUsers);
        y = ligne(form, g, y, "Agences max :", fMaxAgences);
        y = ligne(form, g, y, "Version min / max :", duo(fVersionMin, fVersionMax));
        y = ligne(form, g, y, "Empreinte serveur :",
                ligneAvecBouton(fEmpreinte, "Charger .licreq…", this::chargerLicreq));

        JPanel modules = new JPanel(new GridLayout(0, 4, 6, 2));
        modules.setBorder(BorderFactory.createTitledBorder("Modules activés (vide = tous)"));
        for (int i = 0; i < MODULES.length; i++) {
            cModules[i] = new JCheckBox(MODULES[i], true);
            modules.add(cModules[i]);
        }
        g.gridx = 0; g.gridy = y; g.gridwidth = 2; g.fill = GridBagConstraints.HORIZONTAL;
        form.add(modules, g);

        JButton generer = new JButton("🔏 Générer la licence (.lic)");
        generer.setFont(generer.getFont().deriveFont(Font.BOLD));
        generer.addActionListener(e -> genererLicence());
        JPanel bas = new JPanel(new BorderLayout(6, 6));
        bas.setBorder(new EmptyBorder(4, 12, 10, 12));
        bas.add(generer, BorderLayout.NORTH);
        sortie.setEditable(false);
        sortie.setLineWrap(true);
        bas.add(new JScrollPane(sortie), BorderLayout.CENTER);

        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(form), BorderLayout.CENTER);
        p.add(bas, BorderLayout.SOUTH);
        return p;
    }

    private void genererLicence() {
        try {
            PrivateKey pk = lireClePrivee(Path.of(cheminClePrivee.getText().trim()));
            StringBuilder mods = new StringBuilder();
            for (JCheckBox c : cModules) {
                if (c.isSelected()) { mods.append(mods.length() > 0 ? "," : "").append(c.getText()); }
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("clientId", fClientId.getText().trim());
            payload.put("societe", fSociete.getText().trim());
            payload.put("pays", fPays.getText().trim());
            payload.put("email", fEmail.getText().trim());
            payload.put("type", String.valueOf(fType.getSelectedItem()));
            payload.put("dateActivation", fActivation.getText().trim());
            payload.put("dateExpiration", fExpiration.getText().trim());
            payload.put("maxUsers", entier(fMaxUsers.getText()));
            payload.put("maxAgences", entier(fMaxAgences.getText()));
            payload.put("modules", mods.toString());
            payload.put("versionMin", fVersionMin.getText().trim());
            payload.put("versionMax", fVersionMax.getText().trim());
            payload.put("empreinteServeur", fEmpreinte.getText().trim());

            String json = enJson(payload);
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(pk);
            sig.update(json.getBytes(StandardCharsets.UTF_8));
            String cle = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8))
                    + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign());

            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("LICENSE-" + fClientId.getText().trim() + ".lic"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                Files.writeString(fc.getSelectedFile().toPath(), cle, StandardCharsets.UTF_8);
            }
            sortie.setText("Clé d'activation (équivalente au fichier .lic) :\n\n" + cle);
            historiser(payload);
            JOptionPane.showMessageDialog(this,
                    "Licence générée pour « " + fSociete.getText().trim() + " » (expire le "
                            + fExpiration.getText().trim() + ").",
                    "Licence générée", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Génération impossible : " + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Charge un REQUEST.licreq (base64url JSON) : remplit empreinte / société / e-mail. */
    private void chargerLicreq() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) { return; }
        try {
            String brut = Files.readString(fc.getSelectedFile().toPath(), StandardCharsets.UTF_8).trim();
            String json = new String(Base64.getUrlDecoder().decode(brut), StandardCharsets.UTF_8);
            String emp = champJson(json, "empreinteServeur");
            String soc = champJson(json, "societe");
            String mail = champJson(json, "email");
            if (emp != null) { fEmpreinte.setText(emp); }
            if (soc != null && !soc.isEmpty()) { fSociete.setText(soc); }
            if (mail != null && !mail.isEmpty()) { fEmail.setText(mail); }
            JOptionPane.showMessageDialog(this, "Demande chargée (empreinte : " + emp + ").");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Fichier .licreq illisible : " + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    /* ------------------------------- Onglet Clés ------------------------------- */

    private JPanel ongletCles() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(new EmptyBorder(14, 14, 14, 14));
        JTextArea aide = new JTextArea(
                "Génère la paire de clés RSA 2048 de l'éditeur.\n\n"
                + "  • private.pem  → reste chez VOUS (jamais livrée, jamais dans un dépôt public).\n"
                + "  • public.pem   → à copier dans l'application avant build du WAR :\n"
                + "                   src/main/resources/licence/public.pem\n\n"
                + "⚠ Régénérer les clés invalide toutes les licences déjà émises avec l'ancienne paire.");
        aide.setEditable(false);
        aide.setBackground(p.getBackground());
        JButton btn = new JButton("🗝️ Générer une nouvelle paire de clés…");
        btn.addActionListener(e -> genererCles());
        p.add(aide, BorderLayout.CENTER);
        p.add(btn, BorderLayout.SOUTH);
        return p;
    }

    private void genererCles() {
        try {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("Dossier de destination des clés");
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) { return; }
            Path dossier = fc.getSelectedFile().toPath();
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            Files.writeString(dossier.resolve("private.pem"),
                    pem("PRIVATE KEY", kp.getPrivate().getEncoded()), StandardCharsets.UTF_8);
            Files.writeString(dossier.resolve("public.pem"),
                    pem("PUBLIC KEY", kp.getPublic().getEncoded()), StandardCharsets.UTF_8);
            cheminClePrivee.setText(dossier.resolve("private.pem").toString());
            JOptionPane.showMessageDialog(this,
                    "Clés générées dans " + dossier + "\n\nCopiez public.pem dans l'application "
                    + "(src/main/resources/licence/public.pem) puis rebuild du WAR.",
                    "Clés générées", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Génération impossible : " + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    /* -------------------------------- utilitaires -------------------------------- */

    private void choisirClePrivee() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            cheminClePrivee.setText(fc.getSelectedFile().getPath());
        }
    }

    private static PrivateKey lireClePrivee(Path chemin) throws Exception {
        String pem = Files.readString(chemin, StandardCharsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }

    private static String pem(String type, byte[] der) {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + b64 + "\n-----END " + type + "-----\n";
    }

    /** JSON compact avec échappement minimal (pas de dépendance externe). */
    private static String enJson(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean premier = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            Object v = e.getValue();
            if (v == null || (v instanceof String && ((String) v).isEmpty())) { continue; }
            if (!premier) { sb.append(','); }
            premier = false;
            sb.append('"').append(e.getKey()).append("\":");
            if (v instanceof Number) { sb.append(v); }
            else {
                sb.append('"').append(String.valueOf(v)
                        .replace("\\", "\\\\").replace("\"", "\\\"")
                        .replace("\n", " ").replace("\r", " ")).append('"');
            }
        }
        return sb.append('}').toString();
    }

    private static String champJson(String json, String champ) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(champ) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static Integer entier(String s) {
        try { return Integer.valueOf(s.trim()); } catch (RuntimeException e) { return null; }
    }

    /** Historique local des licences émises (une ligne JSON par licence). */
    private void historiser(Map<String, Object> payload) {
        try {
            Files.writeString(Path.of("licences_generees.log"),
                    LocalDateTime.now() + " " + enJson(payload) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignore) { }
    }

    private static int ligne(JPanel p, GridBagConstraints g, int y, String label, Component champ) {
        g.gridx = 0; g.gridy = y; g.gridwidth = 1; g.fill = GridBagConstraints.NONE;
        p.add(new JLabel(label), g);
        g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
        p.add(champ, g);
        g.weightx = 0;
        return y + 1;
    }

    private static JPanel duo(Component a, Component b) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.add(a); p.add(new JLabel("/")); p.add(b);
        return p;
    }

    private JPanel ligneAvecBouton(JTextField champ, String texteBouton, Runnable action) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.add(champ, BorderLayout.CENTER);
        JButton b = new JButton(texteBouton);
        b.addActionListener(e -> action.run());
        p.add(b, BorderLayout.EAST);
        return p;
    }
}
