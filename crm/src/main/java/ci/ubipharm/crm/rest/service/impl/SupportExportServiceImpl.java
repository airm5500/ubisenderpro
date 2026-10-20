package ci.ubipharm.crm.rest.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.SupportExperienceService;
import ci.ubipharm.crm.rest.service.SupportExportService;
import ci.ubipharm.crm.rest.service.SupportSanteService;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.SupportExperienceDTO;
import ci.ubipharm.crm.rest.service.dto.SupportExperienceLigneDTO;
import ci.ubipharm.crm.rest.service.dto.SupportSondeDTO;

/** Fichiers CSV separes par des points-virgules, en UTF-8 avec BOM (ouverture directe dans Excel) ; volumes plafonnes. */
@Service
public class SupportExportServiceImpl implements SupportExportService {

    static final int MAX_LIGNES = 500;
    static final int MAX_DETAIL = 4000;
    static final int MAX_JOURNAL = 1024 * 1024;
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final JdbcTemplate jdbc;
    private final SupportSanteService supportSanteService;
    private final SupportExperienceService supportExperienceService;
    private final JournalActionService journalActionService;
    private final String journal;

    public SupportExportServiceImpl(JdbcTemplate jdbc, SupportSanteService supportSanteService, SupportExperienceService supportExperienceService,
            JournalActionService journalActionService, @Value("${logging.file.name:}") String journal) {
        this.jdbc = jdbc;
        this.supportSanteService = supportSanteService;
        this.supportExperienceService = supportExperienceService;
        this.journalActionService = journalActionService;
        this.journal = journal;
    }

    @Override
    public FichierDTO exporter() {
        LocalDateTime maintenant = LocalDateTime.now(ZoneOffset.UTC);
        ByteArrayOutputStream octets = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(octets, StandardCharsets.UTF_8)) {
            ajouter(zip, "info.txt", info(maintenant).getBytes(StandardCharsets.UTF_8), false);
            ajouter(zip, "erreurs.csv", erreurs().getBytes(StandardCharsets.UTF_8), true);
            ajouter(zip, "tickets.csv", tickets().getBytes(StandardCharsets.UTF_8), true);
            ajouter(zip, "experience.csv", experience().getBytes(StandardCharsets.UTF_8), true);
            byte[] fin = finDuJournal();
            if (fin != null) {
                ajouter(zip, "journal.txt", fin, false);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        journalActionService.enregistrer("SUPPORT_EXPORT", "crm_support_erreur", null, "Dossier de diagnostic exporté");
        String nom = "diagnostic-crm-" + maintenant.atZone(ZoneOffset.UTC).withZoneSameInstant(ABIDJAN).format(HORODATAGE) + ".zip";
        return FichierDTO.builder().nom(nom).typeContenu("application/zip").contenu(octets.toByteArray()).build();
    }

    private String info(LocalDateTime maintenant) {
        StringBuilder texte = new StringBuilder("Dossier de diagnostic du CRM UBIPHARM\n");
        texte.append("Exporté le ").append(maintenant.atZone(ZoneOffset.UTC).withZoneSameInstant(ABIDJAN)
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).append(" (heure d'Abidjan)\n");
        texte.append("Java ").append(Runtime.version()).append(" · ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append(" · ").append(Runtime.getRuntime().availableProcessors()).append(" processeur(s)\n\n");
        texte.append("Santé de l'application\n");
        for (SupportSondeDTO s : supportSanteService.sonder()) {
            texte.append("- ").append(s.getLibelle()).append(" : ").append(s.getEtat()).append(" · ").append(s.getDetail()).append('\n');
        }
        return texte.toString();
    }

    private String erreurs() {
        StringBuilder csv = new StringBuilder("Origine;Type;Message;Ecran ou adresse;Survenues;Premiere (UTC);Derniere (UTC);Traitee;Ticket;Detail\n");
        jdbc.query("""
                select origine, type_erreur, message, chemin, occurrences, premiere_le, derniere_le, traite, ticket_id, detail
                from crm_support_erreur order by derniere_le desc limit ?
                """, rs -> {
            ligne(csv, rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                    rs.getBoolean(8) ? "oui" : "non", rs.getString(9), tronquer(rs.getString(10), MAX_DETAIL));
        }, MAX_LIGNES);
        return csv.toString();
    }

    private String tickets() {
        StringBuilder csv = new StringBuilder("N°;Sujet;Categorie;Priorite;Statut;Demandeur;Pris en charge par;Ouvert le (UTC);Resolu le (UTC);Ferme le (UTC)\n");
        jdbc.query("""
                select t.id, t.sujet, t.categorie, t.priorite, t.statut, coalesce(a.nom_complet, 'Système'), s.nom_complet,
                       t.created_at, t.resolu_le, t.ferme_le
                from crm_support_ticket t
                left join crm_utilisateur a on a.id = t.auteur_id
                left join crm_utilisateur s on s.id = t.assigne_id
                order by t.id desc limit ?
                """, rs -> {
            ligne(csv, rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                    rs.getString(8), rs.getString(9), rs.getString(10));
        }, MAX_LIGNES);
        return csv.toString();
    }

    private String experience() {
        SupportExperienceDTO e = supportExperienceService.consulter(24);
        StringBuilder csv = new StringBuilder("Type;Ecran ou appel;Mesures;Moyenne (ms);Pire (ms);Part >= 1 s (%);Part >= 3 s (%);Echecs\n");
        lignes(csv, "Écran", e.getEcrans());
        lignes(csv, "Appel", e.getAppels());
        return csv.toString();
    }

    private static void lignes(StringBuilder csv, String type, List<SupportExperienceLigneDTO> lignes) {
        for (SupportExperienceLigneDTO l : lignes) {
            ligne(csv, type, l.getCle(), String.valueOf(l.getNombre()), String.valueOf(l.getMoyenneMs()), String.valueOf(l.getMaxMs()),
                    String.valueOf(l.getPart1s()).replace('.', ','), String.valueOf(l.getPart3s()).replace('.', ','), String.valueOf(l.getEchecs()));
        }
    }

    /** Le dernier Mo du journal du serveur, s'il est ecrit dans un fichier (installation Windows). */
    private byte[] finDuJournal() {
        if (journal == null || journal.isBlank() || !new File(journal).isFile()) {
            return null;
        }
        try (RandomAccessFile f = new RandomAccessFile(journal, "r")) {
            long taille = f.length();
            int lus = (int) Math.min(taille, MAX_JOURNAL);
            byte[] fin = new byte[lus];
            f.seek(taille - lus);
            f.readFully(fin);
            return fin;
        } catch (IOException e) {
            return null;
        }
    }

    private static void ajouter(ZipOutputStream zip, String nom, byte[] contenu, boolean bom) throws IOException {
        zip.putNextEntry(new ZipEntry(nom));
        if (bom) {
            zip.write(BOM);
        }
        zip.write(contenu);
        zip.closeEntry();
    }

    static void ligne(StringBuilder csv, String... valeurs) {
        for (int i = 0; i < valeurs.length; i++) {
            if (i > 0) {
                csv.append(';');
            }
            String v = valeurs[i] == null ? "" : valeurs[i];
            if (v.indexOf(';') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
                v = '"' + v.replace("\"", "\"\"") + '"';
            }
            csv.append(v);
        }
        csv.append('\n');
    }

    private static String tronquer(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max) + "…";
    }
}
