package ci.ubipharm.crm.rest.service.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.CharteService;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.CharteDTO;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CharteServiceImpl implements CharteService {

    private static final long TAILLE_MAX = 5L * 1024L * 1024L;
    /** Un SVG s'affiche dans la page : ni script, ni gestionnaire d'evenement, ni ressource externe. */
    private static final Pattern SVG_DANGEREUX = Pattern.compile("(?i)<script|\\son[a-z]+\\s*=|javascript:|<foreignobject|href\\s*=\\s*[\"']?(https?:|//)");

    private final NamedParameterJdbcTemplate jdbc;
    private final ParametreService parametreService;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;

    @Override
    @Transactional(readOnly = true)
    public CharteDTO charte() {
        CharteDTO.CharteDTOBuilder charte = CharteDTO.builder()
                .nom(parametreService.valeur("app.nom", "UBIPHARM CRM"))
                .couleurPrincipale(parametreService.valeur("charte.couleur_principale", "#05253D"))
                .couleurSecondaire(parametreService.valeur("charte.couleur_secondaire", "#51D78E"))
                .theme(parametreService.valeur("charte.theme", "A"))
                .couleurFond(parametreService.valeur("charte.couleur_fond", "#EEF1F4"))
                .couleurSurvol(parametreService.valeur("charte.couleur_survol", "#E6F8EE"))
                .siteWeb(parametreService.valeur("entreprise.site_web", null))
                .canalDefaut(parametreService.valeur("envoi.canal_defaut", "WHATSAPP"))
                .modeEnvoiDefaut(parametreService.valeur("whatsapp.mode_envoi_defaut", "WA_WEB"));
        jdbc.query("select code, id, nom_fichier from crm_charte_fichier where actif = true", Map.of(), rs -> {
            switch (rs.getString(1)) {
                case "LOGO" -> charte.logoVersion(rs.getLong(2)).logoNom(rs.getString(3));
                case "BANNIERE" -> charte.banniereVersion(rs.getLong(2)).banniereNom(rs.getString(3));
                default -> charte.iconeVersion(rs.getLong(2)).iconeNom(rs.getString(3));
            }
        });
        return charte.build();
    }

    @Override
    @Transactional(readOnly = true)
    public FichierDTO fichier(String code) {
        List<FichierDTO> fichiers = jdbc.query("select nom_fichier, type_mime, contenu from crm_charte_fichier where code = :code and actif = true",
                Map.of("code", verifierCode(code)),
                (rs, i) -> FichierDTO.builder().nom(rs.getString(1)).typeContenu(rs.getString(2)).contenu(rs.getBytes(3)).build());
        return fichiers.isEmpty() ? null : fichiers.getFirst();
    }

    @Override
    @Transactional
    public CharteDTO deposer(String code, MultipartFile fichier) {
        String c = verifierCode(code);
        String libelle = switch (c) {
            case "LOGO" -> "Le logo";
            case "BANNIERE" -> "La bannière";
            default -> "L'icône";
        };
        if (fichier == null || fichier.isEmpty()) {
            throw new ErreurMetierException("Choisissez le fichier " + switch (c) {
                case "LOGO" -> "du logo.";
                case "BANNIERE" -> "de la bannière.";
                default -> "de l'icône.";
            });
        }
        if (fichier.getSize() > TAILLE_MAX) {
            throw new ErreurMetierException(libelle + " dépasse 5 Mo : réduisez-le avant de le déposer.");
        }
        byte[] contenu;
        try {
            contenu = fichier.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String type = typeReel(contenu, "ICONE".equals(c));
        if ("BANNIERE".equals(c) && "image/svg+xml".equals(type)) {
            // WhatsApp n'affiche pas un SVG : la banniere part en image dans les messages
            type = null;
        }
        if ("BANNIERE".equals(c) && type == null) {
            throw new ErreurMetierException("La bannière doit être une image PNG ou JPEG : elle part dans les messages WhatsApp.");
        }
        if (type == null) {
            throw new ErreurMetierException(libelle + " doit être une image PNG, JPEG ou SVG" + ("ICONE".equals(c) ? ", ou un fichier .ico." : "."));
        }
        String nom = fichier.getOriginalFilename() == null || fichier.getOriginalFilename().isBlank() ? c.toLowerCase(Locale.ROOT)
                : fichier.getOriginalFilename().replaceAll(".*[\\\\/]", "").replaceAll("[\\\\/:*?\"<>|]", "_");
        if (nom.length() > 200) {
            nom = nom.substring(nom.length() - 200);
        }
        jdbc.update("update crm_charte_fichier set actif = false where code = :code", Map.of("code", c));
        jdbc.update("""
                insert into crm_charte_fichier (code, nom_fichier, type_mime, contenu, actif, cree_par, created_at)
                values (:code, :nom, :type, :contenu, true, :par, utc_timestamp())
                """, new MapSqlParameterSource().addValue("code", c).addValue("nom", nom).addValue("type", type).addValue("contenu", contenu)
                .addValue("par", utilisateurCourantService.utilisateur().getId()));
        journalActionService.enregistrer("CHARTE_MODIFICATION", "crm_charte_fichier", null, c + " : " + nom);
        return charte();
    }

    @Override
    @Transactional
    public CharteDTO reinitialiser(String code) {
        String c = verifierCode(code);
        if (jdbc.update("update crm_charte_fichier set actif = false where code = :code and actif = true", Map.of("code", c)) > 0) {
            journalActionService.enregistrer("CHARTE_MODIFICATION", "crm_charte_fichier", null,
                    "BANNIERE".equals(c) ? "BANNIERE : retirée" : c + " : retour à celui livré avec l'application");
        }
        return charte();
    }

    private static String verifierCode(String code) {
        String c = code == null ? "" : code.toUpperCase(Locale.ROOT);
        if (!"LOGO".equals(c) && !"ICONE".equals(c) && !"BANNIERE".equals(c)) {
            throw new RessourceIntrouvableException("Élément de charte inconnu");
        }
        return c;
    }

    /** Type lu dans les premiers octets, pas dans le nom du fichier. */
    private static String typeReel(byte[] o, boolean icone) {
        if (o.length >= 8 && (o[0] & 0xFF) == 0x89 && o[1] == 'P' && o[2] == 'N' && o[3] == 'G') {
            return "image/png";
        }
        if (o.length >= 3 && (o[0] & 0xFF) == 0xFF && (o[1] & 0xFF) == 0xD8 && (o[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (icone && o.length >= 4 && o[0] == 0 && o[1] == 0 && o[2] == 1 && o[3] == 0) {
            return "image/x-icon";
        }
        String texte = new String(o, 0, Math.min(o.length, 4096), StandardCharsets.UTF_8).stripLeading();
        if ((texte.startsWith("<svg") || texte.startsWith("<?xml")) && texte.contains("<svg")) {
            if (SVG_DANGEREUX.matcher(new String(o, StandardCharsets.UTF_8)).find()) {
                throw new ErreurMetierException("Ce SVG contient un script ou un lien externe : exportez-le de nouveau en image simple.");
            }
            return "image/svg+xml";
        }
        return null;
    }
}
