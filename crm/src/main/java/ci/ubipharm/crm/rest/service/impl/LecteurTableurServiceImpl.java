package ci.ubipharm.crm.rest.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.LecteurTableurService;
import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.dto.LigneTableurDTO;
import ci.ubipharm.crm.rest.service.dto.TableurDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LecteurTableurServiceImpl implements LecteurTableurService {

    private static final int LIGNES_MAX_PAR_DEFAUT = 20000;
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private final ParametreService parametreService;

    @Override
    public TableurDTO lire(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new ErreurMetierException("Le fichier est vide.");
        }
        String nom = fichier.getOriginalFilename() == null ? "" : fichier.getOriginalFilename().toLowerCase(Locale.ROOT);
        List<List<String>> brut;
        try {
            if (nom.endsWith(".csv") || nom.endsWith(".txt")) {
                brut = lireCsv(fichier.getBytes());
            } else if (nom.endsWith(".xlsx") || nom.endsWith(".xls")) {
                try (InputStream flux = fichier.getInputStream()) {
                    brut = lireClasseur(flux);
                }
            } else {
                throw new ErreurMetierException("Format non pris en charge : utilisez un fichier Excel (.xlsx, .xls) ou CSV.");
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ErreurMetierException metier) {
                throw metier;
            }
            throw new ErreurMetierException("Le fichier n'a pas pu être lu. Vérifiez qu'il n'est pas protégé ou endommagé.");
        }
        return versTableur(brut);
    }

    private TableurDTO versTableur(List<List<String>> brut) {
        if (brut.isEmpty()) {
            throw new ErreurMetierException("Le fichier ne contient aucune ligne.");
        }
        List<String> entetes = brut.getFirst().stream().map(ValeursImport::normaliserEntete).toList();
        int lignesMax = parametreService.entier("import.lignes_max", LIGNES_MAX_PAR_DEFAUT);
        List<LigneTableurDTO> lignes = new ArrayList<>();
        for (int i = 1; i < brut.size(); i++) {
            List<String> cellules = brut.get(i);
            if (cellules.stream().allMatch(c -> c == null || c.isBlank())) {
                continue;
            }
            if (lignes.size() >= lignesMax) {
                throw new ErreurMetierException("Le fichier dépasse " + lignesMax + " lignes : découpez-le en plusieurs imports.");
            }
            Map<String, String> valeurs = new LinkedHashMap<>();
            for (int c = 0; c < entetes.size(); c++) {
                String entete = entetes.get(c);
                if (entete.isEmpty() || valeurs.containsKey(entete)) {
                    continue;
                }
                String valeur = c < cellules.size() ? cellules.get(c) : null;
                valeurs.put(entete, valeur == null ? null : valeur.strip());
            }
            // Numero de ligne tel que l'utilisateur le voit dans son tableur (en-tete = ligne 1)
            lignes.add(LigneTableurDTO.builder().numero(i + 1).valeurs(valeurs).build());
        }
        List<String> entetesOrigine = brut.getFirst().stream().map(e -> e == null ? "" : e.strip()).toList();
        return TableurDTO.builder().entetes(entetes).entetesOrigine(entetesOrigine).lignes(lignes).build();
    }

    private List<List<String>> lireClasseur(InputStream flux) throws IOException {
        List<List<String>> lignes = new ArrayList<>();
        try (Workbook classeur = WorkbookFactory.create(flux)) {
            Sheet feuille = classeur.getSheetAt(0);
            int derniere = feuille.getLastRowNum();
            for (int r = feuille.getFirstRowNum(); r <= derniere; r++) {
                Row ligne = feuille.getRow(r);
                List<String> cellules = new ArrayList<>();
                if (ligne != null) {
                    for (int c = 0; c < Math.max(ligne.getLastCellNum(), 0); c++) {
                        cellules.add(texte(ligne.getCell(c)));
                    }
                }
                lignes.add(cellules);
            }
        }
        // Les lignes vides en tete sont ignorees jusqu'aux en-tetes
        while (!lignes.isEmpty() && lignes.getFirst().stream().allMatch(c -> c == null || c.isBlank())) {
            lignes.removeFirst();
        }
        return lignes;
    }

    private String texte(Cell cellule) {
        if (cellule == null) {
            return null;
        }
        CellType type = cellule.getCellType() == CellType.FORMULA ? cellule.getCachedFormulaResultType() : cellule.getCellType();
        return switch (type) {
            case STRING -> cellule.getStringCellValue();
            case BOOLEAN -> String.valueOf(cellule.getBooleanCellValue());
            case NUMERIC -> DateUtil.isCellDateFormatted(cellule)
                    ? cellule.getLocalDateTimeCellValue().toLocalDate().toString()
                    : BigDecimal.valueOf(cellule.getNumericCellValue()).stripTrailingZeros().toPlainString();
            default -> null;
        };
    }

    private List<List<String>> lireCsv(byte[] octets) {
        String contenu = decoder(octets);
        if (!contenu.isEmpty() && contenu.charAt(0) == '﻿') {
            contenu = contenu.substring(1);
        }
        String premiereLigne = contenu.lines().findFirst().orElse("");
        char separateur = compter(premiereLigne, ';') >= compter(premiereLigne, ',') ? ';' : ',';
        if (compter(premiereLigne, '\t') > compter(premiereLigne, separateur)) {
            separateur = '\t';
        }
        return decouperCsv(contenu, separateur);
    }

    /** UTF-8 si le fichier en est, sinon Windows-1252 (export Excel francais classique). */
    private String decoder(byte[] octets) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(octets)).toString();
        } catch (CharacterCodingException e) {
            return new String(octets, WINDOWS_1252);
        }
    }

    private List<List<String>> decouperCsv(String contenu, char separateur) {
        List<List<String>> lignes = new ArrayList<>();
        List<String> courante = new ArrayList<>();
        StringBuilder champ = new StringBuilder();
        boolean entreGuillemets = false;
        for (int i = 0; i < contenu.length(); i++) {
            char c = contenu.charAt(i);
            if (entreGuillemets) {
                if (c == '"' && i + 1 < contenu.length() && contenu.charAt(i + 1) == '"') {
                    champ.append('"');
                    i++;
                } else if (c == '"') {
                    entreGuillemets = false;
                } else {
                    champ.append(c);
                }
            } else if (c == '"') {
                entreGuillemets = true;
            } else if (c == separateur) {
                courante.add(champ.toString());
                champ.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < contenu.length() && contenu.charAt(i + 1) == '\n') {
                    i++;
                }
                courante.add(champ.toString());
                champ.setLength(0);
                lignes.add(courante);
                courante = new ArrayList<>();
            } else {
                champ.append(c);
            }
        }
        if (champ.length() > 0 || !courante.isEmpty()) {
            courante.add(champ.toString());
            lignes.add(courante);
        }
        while (!lignes.isEmpty() && lignes.getFirst().stream().allMatch(String::isBlank)) {
            lignes.removeFirst();
        }
        return lignes;
    }

    private int compter(String texte, char c) {
        return (int) texte.chars().filter(x -> x == c).count();
    }
}
