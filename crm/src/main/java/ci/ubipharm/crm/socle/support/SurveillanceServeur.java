package ci.ubipharm.crm.socle.support;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.rest.service.SupportDiagnosticService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Surveillance du serveur, reprise du centre de support de Prestige. Chaque alerte part au diagnostic (origine SURVEILLANCE),
 * ce qui ouvre un ticket automatique :
 * <ul>
 * <li>arret brutal : un fichier temoin reecrit toutes les 2 minutes et efface a l'arret normal ; present au demarrage, il signale
 * un arret sans fermeture (coupure de courant, redemarrage force, processus tue) avec la duree d'indisponibilite et la fin du journal ;</li>
 * <li>toutes les 5 minutes : memoire, connexions a la base, disque d'archivage, service WhatsApp Web. Une alerte demande
 * 3 releves consecutifs (le disque excepte) et ne se repete pas avant une heure.</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "crm.support.surveillance", havingValue = "true", matchIfMissing = true)
public class SurveillanceServeur {

    static final int RELEVES = 3;
    static final Duration SILENCE = Duration.ofHours(1);
    private static final DateTimeFormatter AFFICHAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final SupportDiagnosticService diagnostic;
    private final ParametreService parametreService;
    private final PasserelleWhatsappService passerelle;
    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final Path temoin;
    private final String journal;
    private final Instant demarre = Instant.now();

    private final Map<String, Integer> consecutifs = new HashMap<>();
    private final Map<String, Instant> dernieresAlertes = new ConcurrentHashMap<>();

    public SurveillanceServeur(SupportDiagnosticService diagnostic, ParametreService parametreService, PasserelleWhatsappService passerelle,
            DataSource source, JdbcTemplate jdbc, @Value("${crm.support.temoin:temoin-marche.properties}") String temoin,
            @Value("${logging.file.name:}") String journal) {
        this.diagnostic = diagnostic;
        this.parametreService = parametreService;
        this.passerelle = passerelle;
        this.source = source;
        this.jdbc = jdbc;
        this.temoin = Path.of(temoin).toAbsolutePath();
        this.journal = journal;
    }

    // ---------------------------------------------------------------- arret brutal

    @EventListener(ApplicationReadyEvent.class)
    public void auDemarrage() {
        try {
            verifierArretPrecedent();
        } catch (RuntimeException e) {
            log.warn("Verification de l'arret precedent impossible : {}", e.toString());
        }
        ecrireTemoin();
    }

    @Scheduled(fixedDelay = 120_000, initialDelay = 120_000)
    public void ecrireTemoin() {
        try {
            Runtime jvm = Runtime.getRuntime();
            Properties p = new Properties();
            p.setProperty("vie", Instant.now().toString());
            p.setProperty("demarre", demarre.toString());
            p.setProperty("memoire", (jvm.totalMemory() - jvm.freeMemory()) / (1024 * 1024) + " Mo utilisés sur " + jvm.maxMemory() / (1024 * 1024) + " Mo");
            Path provisoire = temoin.resolveSibling(temoin.getFileName() + ".tmp");
            try (var sortie = Files.newBufferedWriter(provisoire, StandardCharsets.UTF_8)) {
                p.store(sortie, "Temoin de marche du CRM : efface a l'arret normal");
            }
            Files.move(provisoire, temoin, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            log.warn("Ecriture du temoin de marche {} impossible : {}", temoin, e.toString());
        }
    }

    @PreDestroy
    public void arretNormal() {
        try {
            Files.deleteIfExists(temoin);
        } catch (IOException e) {
            log.warn("Effacement du temoin de marche impossible : {}", e.toString());
        }
    }

    void verifierArretPrecedent() {
        if (!Files.exists(temoin)) {
            return;
        }
        Properties p = new Properties();
        try (var entree = Files.newBufferedReader(temoin, StandardCharsets.UTF_8)) {
            p.load(entree);
        } catch (IOException e) {
            return;
        }
        Instant vie = instant(p.getProperty("vie"));
        StringBuilder detail = new StringBuilder("Le serveur s'est arrêté sans passer par un arrêt normal du service.\n");
        if (vie != null) {
            long minutes = Math.max(0, Duration.between(vie, demarre).toMinutes());
            detail.append("Dernier signe de vie : ").append(heure(vie)).append(" (à 2 minutes près)\n");
            detail.append("Redémarré le : ").append(heure(demarre)).append('\n');
            detail.append("Indisponibilité : environ ").append(minutes).append(" min\n");
        }
        Instant precedent = instant(p.getProperty("demarre"));
        if (precedent != null) {
            detail.append("Il tournait depuis le : ").append(heure(precedent)).append('\n');
        }
        if (p.getProperty("memoire") != null) {
            detail.append("Mémoire au dernier relevé : ").append(p.getProperty("memoire")).append('\n');
        }
        String plantage = plantageJava(vie);
        detail.append("\nCause probable : ").append(plantage != null
                ? "plantage de la machine Java (" + plantage + ")."
                : "coupure de courant, redémarrage forcé de Windows ou processus arrêté de force (Gestionnaire des tâches).");
        String fin = finDuJournal();
        if (fin != null) {
            detail.append("\n\nFin du journal avant l'arrêt :\n").append(fin);
        }
        diagnostic.signaler("SURVEILLANCE", "Arrêt brutal du serveur", "Le serveur s'est arrêté sans arrêt normal", detail.toString(), null, null);
    }

    /** Fichier hs_err_pid ecrit par Java en plantant, apres le dernier signe de vie. */
    private String plantageJava(Instant vie) {
        File[] fichiers = new File(".").listFiles((d, nom) -> nom.startsWith("hs_err_pid") && nom.endsWith(".log"));
        if (fichiers == null) {
            return null;
        }
        for (File f : fichiers) {
            if (vie == null || f.lastModified() >= vie.toEpochMilli() - 300_000) {
                return f.getName();
            }
        }
        return null;
    }

    /** Les 40 lignes du journal precedant le demarrage en cours (lues dans les 256 derniers Ko). */
    String finDuJournal() {
        if (journal == null || journal.isBlank() || !new File(journal).isFile()) {
            return null;
        }
        try (RandomAccessFile f = new RandomAccessFile(journal, "r")) {
            long taille = f.length();
            int lus = (int) Math.min(taille, 256 * 1024);
            byte[] octets = new byte[lus];
            f.seek(taille - lus);
            f.readFully(octets);
            String texte = new String(octets, StandardCharsets.UTF_8);
            int debut = texte.lastIndexOf("Starting CrmApplication");
            if (debut > 0) {
                texte = texte.substring(0, texte.lastIndexOf('\n', debut) + 1);
            }
            String[] lignes = texte.split("\n");
            StringBuilder fin = new StringBuilder();
            for (int i = Math.max(0, lignes.length - 40); i < lignes.length; i++) {
                fin.append(lignes[i]).append('\n');
            }
            return fin.toString().isBlank() ? null : fin.toString();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- releves

    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void surveiller() {
        releve(this::memoire);
        releve(this::pool);
        releve(this::disque);
        releve(this::whatsapp);
    }

    private void releve(Runnable mesure) {
        try {
            mesure.run();
        } catch (RuntimeException e) {
            log.warn("Releve de surveillance en echec : {}", e.toString());
        }
    }

    private void memoire() {
        Runtime jvm = Runtime.getRuntime();
        long max = jvm.maxMemory();
        long utilisee = jvm.totalMemory() - jvm.freeMemory();
        int seuil = parametreService.entier("support.memoire_alerte_pct", 85);
        long pct = max > 0 ? utilisee * 100 / max : 0;
        if (alerte("MEMOIRE", pct >= seuil, RELEVES)) {
            diagnostic.signaler("SURVEILLANCE", "Mémoire du serveur", "La mémoire utilisée dépasse le seuil d'alerte",
                    "Mémoire utilisée : " + utilisee / (1024 * 1024) + " Mo sur " + max / (1024 * 1024) + " Mo (" + pct + " %, seuil " + seuil
                            + " %) sur " + RELEVES + " relevés consécutifs.\nAugmentez -Xmx dans crm-service.xml ou redémarrez le service "
                            + "si l'alerte persiste.", null, null);
        }
    }

    private void pool() {
        EtatPool etat = EtatPool.lire(source);
        if (etat != null && alerte("POOL", etat.enAttente() > 0, RELEVES)) {
            diagnostic.signaler("SURVEILLANCE", "Connexions à la base", "Des requêtes attendent une connexion à la base de données",
                    etat.enAttente() + " requête(s) en attente d'une connexion ; " + etat.actives() + " connexion(s) occupée(s) sur "
                            + etat.maximum() + " au maximum, " + RELEVES + " relevés consécutifs.\nUne requête lente peut retenir les "
                            + "connexions : voir les lignes Lenteur du diagnostic.", null, null);
        }
    }

    private void disque() {
        if (!parametreService.oui("archivage.actif", true)) {
            return;
        }
        String repertoire = parametreService.valeur("archivage.repertoire", null);
        if (repertoire == null || !new File(repertoire).isDirectory()) {
            return;
        }
        long libreMo = new File(repertoire).getUsableSpace() / (1024 * 1024);
        int minimumMo = parametreService.entier("support.espace_disque_min_mo", 1024);
        if (alerte("DISQUE", libreMo < minimumMo, 1)) {
            diagnostic.signaler("SURVEILLANCE", "Espace disque", "L'espace libre du disque d'archivage est sous le minimum",
                    repertoire + " : " + libreMo + " Mo libres (minimum " + minimumMo + " Mo).", null, null);
        }
    }

    private void whatsapp() {
        Integer numeros = jdbc.queryForObject("select count(*) from crm_session_whatsapp where fournisseur = 'WA_WEB'", Integer.class);
        if (numeros == null || numeros == 0) {
            return;
        }
        if (alerte("WHATSAPP", !passerelle.joignable(), RELEVES)) {
            diagnostic.signaler("SURVEILLANCE", "Service WhatsApp Web", "Le service WhatsApp Web ne répond pas",
                    "Le service wa-web ne répond plus depuis " + RELEVES + " relevés (environ " + RELEVES * 5 + " min) : les envois "
                            + "WhatsApp Web sont arrêtés. Vérifiez le service « UBIPHARM CRM - WhatsApp » dans les services Windows.", null, null);
        }
    }

    /** Vrai quand la condition tient sur releves consecutifs et qu'aucune alerte du meme code n'est partie depuis une heure. */
    boolean alerte(String code, boolean condition, int releves) {
        if (!condition) {
            consecutifs.remove(code);
            return false;
        }
        int n = consecutifs.merge(code, 1, Integer::sum);
        if (n < releves) {
            return false;
        }
        Instant derniere = dernieresAlertes.get(code);
        if (derniere != null && Duration.between(derniere, Instant.now()).compareTo(SILENCE) < 0) {
            return false;
        }
        consecutifs.remove(code);
        dernieresAlertes.put(code, Instant.now());
        return true;
    }

    private static Instant instant(String valeur) {
        try {
            return valeur == null ? null : Instant.parse(valeur);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String heure(Instant instant) {
        return LocalDateTime.ofInstant(instant, ABIDJAN).format(AFFICHAGE);
    }
}
