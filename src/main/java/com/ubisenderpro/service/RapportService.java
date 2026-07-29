package com.ubisenderpro.service;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Impressions PDF sur modèles JasperReports (.jrxml).
 *
 * <p>Les modèles standard sont embarqués dans le livrable sous
 * {@code /reports/&lt;nom&gt;.jrxml}. Le paramètre {@code rapports.repertoire}
 * peut désigner un répertoire du serveur : un fichier du même nom y REMPLACE le
 * modèle embarqué, ce qui permet d'ajuster une mise en page sans redéploiement.
 * Les modèles compilés sont mis en cache ; un modèle externe modifié (date du
 * fichier) est recompilé automatiquement.</p>
 *
 * <p>Chaque rapport reçoit d'office les coordonnées de la société
 * (SOCIETE_NOM / SOCIETE_ADRESSE / SOCIETE_TEL / SOCIETE_SITE) et son LOGO
 * (flux image, ou {@code null} si aucun logo n'est configuré) : l'en-tête des
 * documents est ainsi le même partout.</p>
 */
@Stateless
public class RapportService {

    private static final Logger LOG = Logger.getLogger(RapportService.class.getName());

    /** Noms de modèles admissibles : pas de traversée de répertoire possible. */
    private static final Pattern NOM_VALIDE = Pattern.compile("[a-z0-9_\\-]+");

    /** Répertoire des modèles si le paramètre n'est pas renseigné. */
    public static final String REPERTOIRE_DEFAUT = "D:\\REPORTS";

    /**
     * Modèles embarqués dans le livrable (src/main/resources/reports). Ils sont
     * déposés dans le répertoire des rapports au démarrage s'ils n'y figurent
     * pas déjà : l'exploitant les retrouve en clair et peut les personnaliser.
     */
    public static final String[] MODELES_EMBARQUES = { "clients" };

    private static final Pattern MEDIA_ID = Pattern.compile("/media/(\\d+)\\b");

    /** Cache des modèles compilés, partagé entre instances du bean. */
    private static final ConcurrentHashMap<String, ModeleCompile> CACHE = new ConcurrentHashMap<>();

    private static final class ModeleCompile {
        final JasperReport rapport;
        /** Chemin du fichier externe d'origine, null pour un modèle embarqué. */
        final String fichier;
        final long dateFichier;
        ModeleCompile(JasperReport rapport, String fichier, long dateFichier) {
            this.rapport = rapport; this.fichier = fichier; this.dateFichier = dateFichier;
        }
    }

    @EJB
    private ParametreService parametreService;
    @EJB
    private MediaFichierService mediaFichierService;

    /**
     * Génère le PDF du rapport {@code nom} avec les lignes fournies.
     *
     * @param nom    nom du modèle, sans extension (ex. « clients »)
     * @param params paramètres propres au rapport (TITRE, SOUS_TITRE…) ; les
     *               coordonnées société et le logo sont ajoutés d'office
     * @param lignes données du bandeau détail (une Map par ligne)
     */
    public byte[] pdf(String nom, Map<String, Object> params, List<Map<String, ?>> lignes) {
        if (nom == null || !NOM_VALIDE.matcher(nom).matches()) {
            throw new ValidationException("rapport", "Nom de rapport invalide.");
        }
        try {
            JasperReport rapport = modele(nom);
            Map<String, Object> p = new HashMap<>();
            p.put(net.sf.jasperreports.engine.JRParameter.REPORT_LOCALE, Locale.FRANCE);
            parametresSociete(p);
            if (params != null) { p.putAll(params); }
            @SuppressWarnings("unchecked")
            Collection<Map<String, ?>> donnees = (Collection<Map<String, ?>>) (Collection<?>)
                    (lignes == null ? java.util.Collections.emptyList() : lignes);
            JasperPrint print = JasperFillManager.fillReport(rapport, p,
                    new JRMapCollectionDataSource(donnees));
            return JasperExportManager.exportReportToPdf(print);
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            LOG.severe("RAPPORT_KO nom=" + nom + " : " + e);
            throw new ValidationException("rapport",
                    "Génération du rapport « " + nom + " » impossible : "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /** Coordonnées de la société + logo, injectés dans tous les rapports. */
    private void parametresSociete(Map<String, Object> p) {
        p.put("SOCIETE_NOM", parametreService.valeur("app.societe", ""));
        p.put("SOCIETE_ADRESSE", parametreService.valeur("app.adresse", ""));
        p.put("SOCIETE_TEL", parametreService.valeur("app.societe_tel", ""));
        p.put("SOCIETE_SITE", parametreService.valeur("app.site", ""));
        p.put("LOGO", fluxLogo());
    }

    /**
     * Logo de la société sous forme de flux, ou null. Le paramètre app.logo
     * contient l'URL publique du média téléversé (…/media/{id}) : on lit le
     * contenu directement en base plutôt que de rappeler l'application en HTTP.
     */
    private InputStream fluxLogo() {
        String url = parametreService.valeur("app.logo", "");
        if (url == null || url.trim().isEmpty()) { return null; }
        Matcher m = MEDIA_ID.matcher(url);
        if (!m.find()) { return null; }
        try {
            return mediaFichierService.parId(Long.valueOf(m.group(1)))
                    .map(mf -> (InputStream) new ByteArrayInputStream(mf.getContenu()))
                    .orElse(null);
        } catch (RuntimeException e) {
            return null; // un logo illisible ne doit pas empêcher l'impression
        }
    }

    /** Modèle compilé (cache + recompilation si le fichier externe a changé). */
    private JasperReport modele(String nom) throws Exception {
        File externe = fichierExterne(nom);
        ModeleCompile en = CACHE.get(nom);
        if (en != null) {
            boolean valide = externe == null
                    ? en.fichier == null
                    : externe.getPath().equals(en.fichier) && externe.lastModified() == en.dateFichier;
            if (valide) { return en.rapport; }
        }
        JasperReport rapport;
        if (externe != null) {
            LOG.info("Rapport « " + nom + " » : modèle personnalisé " + externe.getPath());
            try (InputStream in = new FileInputStream(externe)) {
                rapport = JasperCompileManager.compileReport(in);
            }
            CACHE.put(nom, new ModeleCompile(rapport, externe.getPath(), externe.lastModified()));
        } else {
            InputStream in = getClass().getResourceAsStream("/reports/" + nom + ".jrxml");
            if (in == null) {
                throw new ValidationException("rapport", "Modèle de rapport « " + nom + " » introuvable.");
            }
            try { rapport = JasperCompileManager.compileReport(in); } finally { in.close(); }
            CACHE.put(nom, new ModeleCompile(rapport, null, 0L));
        }
        return rapport;
    }

    /** Répertoire configuré des modèles (D:\REPORTS à défaut). */
    private String repertoire() {
        String dir = parametreService.valeur("rapports.repertoire", REPERTOIRE_DEFAUT);
        return (dir == null || dir.trim().isEmpty()) ? REPERTOIRE_DEFAUT : dir.trim();
    }

    /** Fichier {repertoire}/{nom}.jrxml s'il existe et se lit, sinon null. */
    private File fichierExterne(String nom) {
        File f = new File(repertoire(), nom + ".jrxml");
        return f.isFile() && f.canRead() ? f : null;
    }

    /**
     * Dépose dans le répertoire des rapports les modèles embarqués qui n'y
     * figurent pas encore (jamais d'écrasement : une personnalisation est
     * conservée). Best-effort : sur un serveur sans ce disque, on journalise et
     * l'application fonctionne sur les modèles embarqués.
     *
     * @return nombre de modèles déposés
     */
    public int deployerModeles() {
        File dir = new File(repertoire());
        if (!dir.isDirectory() && !dir.mkdirs()) {
            LOG.info("Rapports : répertoire " + dir.getPath()
                    + " inaccessible — modèles embarqués utilisés.");
            return 0;
        }
        int copies = 0;
        for (String nom : MODELES_EMBARQUES) {
            File cible = new File(dir, nom + ".jrxml");
            if (cible.exists()) { continue; }
            try (InputStream in = getClass().getResourceAsStream("/reports/" + nom + ".jrxml")) {
                if (in == null) { continue; }
                java.nio.file.Files.copy(in, cible.toPath());
                copies++;
            } catch (Exception e) {
                LOG.warning("Rapports : dépôt de " + cible.getPath() + " impossible : " + e.getMessage());
            }
        }
        if (copies > 0) {
            LOG.info("Rapports : " + copies + " modèle(s) .jrxml déposé(s) dans " + dir.getPath());
        }
        return copies;
    }

    /* ------------------------------------------------------------------ */
    /* Aides de mise en forme partagées par les rapports                    */
    /* ------------------------------------------------------------------ */

    /** Ligne de rapport : Map ordonnée où chaque null devient chaîne vide. */
    public static Map<String, Object> ligne() { return new LinkedHashMap<>(); }

    public static String texte(Object v) { return v == null ? "" : String.valueOf(v); }
}
