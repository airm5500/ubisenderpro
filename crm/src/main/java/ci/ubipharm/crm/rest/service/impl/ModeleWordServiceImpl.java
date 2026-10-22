package ci.ubipharm.crm.rest.service.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.ArchivageService;
import ci.ubipharm.crm.rest.service.ModeleMessageService;
import ci.ubipharm.crm.rest.service.ModeleWordService;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.LectureModeleWordDTO;
import ci.ubipharm.crm.rest.service.dto.ModeleMessageDTO;
import ci.ubipharm.crm.rest.service.dto.ModeleMessageDemandeDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ModeleWordServiceImpl implements ModeleWordService {

    static final String TYPE_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    static final String DEBUT = "----- TEXTE DU MESSAGE (début) -----";
    static final String FIN = "----- TEXTE DU MESSAGE (fin) -----";
    private static final int TAILLE_MAX = 4000;
    private static final Set<String> CANAUX = Set.of("WHATSAPP", "EMAIL", "SMS");
    private static final Set<String> TYPES_MARKETING = Set.of("PROMOTION", "EVENEMENT", "INFORMATION");
    /** «nom_pharmacie» (publipostage Word) ou {{nom_pharmacie}} : on garde la forme {nom_pharmacie} du CRM. */
    private static final Pattern PUBLIPOSTAGE = Pattern.compile("«\\s*([A-Za-z0-9_]+)\\s*»|\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*}}");

    private final ModeleMessageService modeleMessageService;
    private final ArchivageService archivageService;

    @Override
    public FichierDTO exporter(String module, Long id) {
        ModeleMessageDTO m = modeleMessageService.consulter(module, id);
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            XWPFRun titre = doc.createParagraph().createRun();
            titre.setBold(true);
            titre.setFontSize(14);
            titre.setText("Modèle de message " + ("MARKETING".equals(module) ? "promotionnel" : "de relance"));
            ligne(doc, "Retouchez le texte entre les deux repères, puis réimportez ce fichier dans le CRM. Les variables s'écrivent {nom_variable}.", false);
            ligne(doc, "", false);
            ligne(doc, "Nom : " + m.getNom(), false);
            ligne(doc, "Type : " + m.getTypeCampagne(), false);
            ligne(doc, "Canal : " + m.getCanal(), false);
            if (m.getObjet() != null && !m.getObjet().isBlank()) {
                ligne(doc, "Objet : " + m.getObjet(), false);
            }
            ligne(doc, "", false);
            ligne(doc, DEBUT, true);
            for (String l : m.getContenu().split("\n", -1)) {
                ligne(doc, l, false);
            }
            ligne(doc, FIN, true);
            doc.write(sortie);
            return FichierDTO.builder().nom(nomFichier(m.getNom()) + ".docx").typeContenu(TYPE_DOCX).contenu(sortie.toByteArray()).build();
        } catch (IOException e) {
            throw new IllegalStateException("Export Word impossible", e);
        }
    }

    @Override
    public LectureModeleWordDTO lire(String module, MultipartFile fichier) {
        String nom = fichier.getOriginalFilename() == null ? "modele.docx" : fichier.getOriginalFilename();
        if (!nom.toLowerCase().endsWith(".docx")) {
            throw new ErreurMetierException("Choisissez un document Word au format .docx (un ancien .doc s'enregistre en .docx depuis Word).");
        }
        byte[] contenu;
        try {
            contenu = fichier.getBytes();
        } catch (IOException e) {
            throw new ErreurMetierException("Le fichier n'a pas pu être lu.");
        }
        List<String> avertissements = new ArrayList<>();
        ModeleMessageDemandeDTO modele;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(contenu))) {
            modele = lire(module, doc, nom, avertissements);
        } catch (IOException | RuntimeException e) {
            if (e instanceof ErreurMetierException metier) {
                throw metier;
            }
            throw new ErreurMetierException("Ce fichier n'est pas un document Word lisible.");
        }
        archivageService.archiver("imports-modeles", nom, contenu);
        return LectureModeleWordDTO.builder().modele(modele).avertissements(avertissements).build();
    }

    private ModeleMessageDemandeDTO lire(String module, XWPFDocument doc, String nomFichier, List<String> avertissements) {
        Map<String, String> champs = new LinkedHashMap<>();
        List<String> texte = new ArrayList<>();
        List<String> tout = new ArrayList<>();
        boolean dedans = false;
        boolean reperes = false;
        for (IBodyElement element : doc.getBodyElements()) {
            if (element instanceof XWPFTable) {
                avertissements.add("Les tableaux du document ne sont pas repris : un message WhatsApp, e-mail ou SMS est du texte.");
                continue;
            }
            if (!(element instanceof XWPFParagraph p)) {
                continue;
            }
            if (p.getRuns().stream().anyMatch(r -> !r.getEmbeddedPictures().isEmpty()) && avertissements.stream().noneMatch(a -> a.startsWith("Les images"))) {
                avertissements.add("Les images du document ne sont pas reprises : joignez-les à la campagne.");
            }
            String t = p.getText();
            String sansEspaces = t.trim();
            if (sansEspaces.equals(DEBUT)) {
                dedans = true;
                reperes = true;
                continue;
            }
            if (sansEspaces.equals(FIN)) {
                dedans = false;
                continue;
            }
            if (dedans) {
                texte.add(t);
                continue;
            }
            tout.add(t);
            int deuxPoints = t.indexOf(" : ");
            if (deuxPoints > 0 && !reperes) {
                champs.putIfAbsent(sansAccents(t.substring(0, deuxPoints).trim()), t.substring(deuxPoints + 3).trim());
            }
        }
        boolean marketing = "MARKETING".equals(module);
        String contenu = String.join("\n", reperes ? texte : tout).strip();
        String nom = reperes && champs.containsKey("NOM") ? champs.get("NOM") : nomFichier.replaceFirst("(?i)\\.docx$", "").replace('_', ' ').trim();
        String type = reperes ? champs.get("TYPE") : null;
        if (type != null && !(marketing ? TYPES_MARKETING.contains(type) : GabaritMessage.TYPES_CAMPAGNE.contains(type))) {
            avertissements.add("Type « " + type + " » inconnu de ce module : choisissez-le dans le formulaire.");
            type = null;
        }
        String canal = reperes ? champs.get("CANAL") : null;
        if (canal != null && !CANAUX.contains(canal)) {
            canal = null;
        }
        contenu = PUBLIPOSTAGE.matcher(contenu).replaceAll(r -> "{" + (r.group(1) != null ? r.group(1) : r.group(2)).toLowerCase() + "}");
        if (contenu.isBlank()) {
            throw new ErreurMetierException("Ce document ne contient aucun texte à reprendre.");
        }
        if (contenu.length() > TAILLE_MAX) {
            avertissements.add("Le texte faisait " + contenu.length() + " caractères : il est coupé à " + TAILLE_MAX + ".");
            contenu = contenu.substring(0, TAILLE_MAX);
        }
        if (nom.length() > 150) {
            nom = nom.substring(0, 150);
        }
        return ModeleMessageDemandeDTO.builder()
                .nom(nom)
                .typeCampagne(type != null ? type : marketing ? "PROMOTION" : "APRES_ECHEANCE")
                .canal(canal)
                .objet(reperes ? champs.get("OBJET") : null)
                .contenu(contenu)
                .build();
    }

    private static void ligne(XWPFDocument doc, String texte, boolean gras) {
        XWPFRun run = doc.createParagraph().createRun();
        run.setBold(gras);
        run.setText(texte);
    }

    private static String sansAccents(String texte) {
        return Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase();
    }

    private static String nomFichier(String nom) {
        String propre = sansAccents(nom).toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        return propre.isEmpty() ? "modele" : propre;
    }
}
