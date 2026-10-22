package ci.ubipharm.crm.rest.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import ci.ubipharm.crm.rest.service.ArchivageService;
import ci.ubipharm.crm.rest.service.ParametreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArchivageServiceImpl implements ArchivageService {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");
    private static final DateTimeFormatter MOIS = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ParametreService parametreService;

    @Override
    public void archiver(String type, String nomFichier, byte[] contenu) {
        if (contenu == null || !parametreService.oui("archivage.actif", true)) {
            return;
        }
        String repertoire = parametreService.valeur("archivage.repertoire", null);
        try {
            Path racine = repertoire == null ? null : Path.of(repertoire);
            if (racine == null || !racine.isAbsolute()) {
                log.warn("Archivage ignore : repertoire {} absent ou non absolu sur ce serveur", repertoire);
                return;
            }
            LocalDateTime maintenant = LocalDateTime.now(ABIDJAN);
            String dossierType = nettoyer(type == null ? "divers" : type);
            Path dossier = switch (parametreService.valeur("archivage.classement", "TYPE_MOIS")) {
                case "MOIS_TYPE" -> racine.resolve(MOIS.format(maintenant)).resolve(dossierType);
                case "TYPE" -> racine.resolve(dossierType);
                default -> racine.resolve(dossierType).resolve(MOIS.format(maintenant));
            };
            Files.createDirectories(dossier);
            String nom = HORODATAGE.format(maintenant) + "_" + nettoyer(nomFichier == null || nomFichier.isBlank() ? "document" : nomFichier);
            Files.write(dossier.resolve(nom), contenu, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException | RuntimeException e) {
            log.warn("Archivage de {} impossible dans {} : {}", nomFichier, repertoire, e.getMessage());
        }
    }

    /** Nom de fichier sans chemin ni caractere interdit sous Windows. */
    private static String nettoyer(String nom) {
        String court = nom.replaceAll(".*[\\\\/]", "").replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return court.length() > 150 ? court.substring(court.length() - 150) : court;
    }
}
