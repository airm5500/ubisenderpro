package ci.ubipharm.crm.rest.service.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.ArchivageService;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.PieceJointeService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.PieceJointeDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PieceJointeServiceImpl implements PieceJointeService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ParametreService parametreService;
    private final ArchivageService archivageService;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;

    @Override
    @Transactional
    public PieceJointeDTO deposerAffiche(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new ErreurMetierException("Choisissez le fichier de l'affiche.");
        }
        String type = typeReel(fichier);
        if (type == null) {
            throw new ErreurMetierException("L'affiche doit être une image JPEG ou PNG, ou un PDF.");
        }
        int maxKo = parametreService.entier("marketing.affiche_taille_max_ko", 5000);
        if (fichier.getSize() > maxKo * 1024L) {
            throw new ErreurMetierException("L'affiche dépasse " + maxKo + " Ko : réduisez-la avant de la joindre.");
        }
        String nom = fichier.getOriginalFilename() == null || fichier.getOriginalFilename().isBlank() ? "affiche" : fichier.getOriginalFilename();
        nom = nom.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (nom.length() > 200) {
            nom = nom.substring(nom.length() - 200);
        }
        try {
            Long id = inserer("AFFICHE", nom, type, fichier.getBytes(), utilisateurCourantService.utilisateur().getId());
            journalActionService.enregistrer("PIECE_JOINTE_DEPOT", "crm_piece_jointe", id, "Affiche " + nom + " (" + fichier.getSize() / 1024 + " Ko)");
            return decrire(id);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PieceJointeDTO decrire(Long id) {
        List<PieceJointeDTO> pieces = jdbc.query("select id, nature, nom_fichier, type_mime, taille from crm_piece_jointe where id = :id",
                Map.of("id", id), (rs, i) -> PieceJointeDTO.builder().id(rs.getLong(1)).nature(rs.getString(2)).nomFichier(rs.getString(3))
                        .typeMime(rs.getString(4)).taille(rs.getInt(5)).build());
        if (pieces.isEmpty()) {
            throw new RessourceIntrouvableException("Pièce jointe introuvable");
        }
        return pieces.getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public FichierDTO lire(Long id) {
        List<FichierDTO> fichiers = jdbc.query("select nom_fichier, type_mime, contenu from crm_piece_jointe where id = :id", Map.of("id", id),
                (rs, i) -> FichierDTO.builder().nom(rs.getString(1)).typeContenu(rs.getString(2)).contenu(rs.getBytes(3)).build());
        if (fichiers.isEmpty()) {
            throw new RessourceIntrouvableException("Pièce jointe introuvable");
        }
        return fichiers.getFirst();
    }

    @Override
    @Transactional
    public Long enregistrerGenere(String nature, FichierDTO fichier) {
        return inserer(nature, fichier.getNom(), fichier.getTypeContenu(), fichier.getContenu(), null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FichierDTO> piecesDeCampagne(Long campagneId) {
        List<Map<String, Object>> lignes = jdbc.queryForList("select affiche_id, liste_produits_id, type_campagne from crm_campagne where id = :id",
                Map.of("id", campagneId));
        List<FichierDTO> pieces = new ArrayList<>();
        if (lignes.isEmpty()) {
            return pieces;
        }
        // Une information sans affiche part sous la banniere de la charte, si elle est deposee
        if ("INFORMATION".equals(lignes.getFirst().get("type_campagne")) && lignes.getFirst().get("affiche_id") == null
                && parametreService.oui("marketing.banniere_informations", true)) {
            pieces.addAll(jdbc.query("select nom_fichier, type_mime, contenu from crm_charte_fichier where code = 'BANNIERE' and actif = true",
                    Map.of(), (rs, i) -> FichierDTO.builder().nom(rs.getString(1)).typeContenu(rs.getString(2)).contenu(rs.getBytes(3)).build()));
        }
        for (String colonne : List.of("affiche_id", "liste_produits_id")) {
            Object id = lignes.getFirst().get(colonne);
            if (id != null) {
                pieces.add(lire(((Number) id).longValue()));
            }
        }
        return pieces;
    }

    private Long inserer(String nature, String nom, String type, byte[] contenu, Long creePar) {
        KeyHolder cle = new GeneratedKeyHolder();
        jdbc.update("""
                insert into crm_piece_jointe (nature, nom_fichier, type_mime, taille, contenu, cree_par, created_at)
                values (:nature, :nom, :type, :taille, :contenu, :creePar, utc_timestamp())
                """, new MapSqlParameterSource().addValue("nature", nature).addValue("nom", nom).addValue("type", type)
                .addValue("taille", contenu.length).addValue("contenu", contenu).addValue("creePar", creePar), cle, new String[] {"id"});
        archivageService.archiver("AFFICHE".equals(nature) ? "affiches" : "listes-produits", nom, contenu);
        return cle.getKey().longValue();
    }

    /** JPEG, PNG ou PDF, formats qu'un telephone ouvre seul ; type lu dans les premiers octets, pas dans le nom du fichier. */
    private static String typeReel(MultipartFile fichier) {
        try {
            byte[] debut = fichier.getInputStream().readNBytes(8);
            if (debut.length >= 3 && (debut[0] & 0xFF) == 0xFF && (debut[1] & 0xFF) == 0xD8 && (debut[2] & 0xFF) == 0xFF) {
                return "image/jpeg";
            }
            if (debut.length >= 8 && (debut[0] & 0xFF) == 0x89 && debut[1] == 'P' && debut[2] == 'N' && debut[3] == 'G') {
                return "image/png";
            }
            if (debut.length >= 5 && debut[0] == '%' && debut[1] == 'P' && debut[2] == 'D' && debut[3] == 'F' && debut[4] == '-') {
                return "application/pdf";
            }
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
