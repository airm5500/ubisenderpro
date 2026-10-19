package ci.ubipharm.crm.rest.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.AssistantImportService;
import ci.ubipharm.crm.rest.service.CorrespondanceImportService;
import ci.ubipharm.crm.rest.service.ImportMetierService;
import ci.ubipharm.crm.rest.service.ImportService;
import ci.ubipharm.crm.rest.service.LecteurTableurService;
import ci.ubipharm.crm.rest.service.dto.ApercuImportDTO;
import ci.ubipharm.crm.rest.service.dto.ChampImportDTO;
import ci.ubipharm.crm.rest.service.dto.ColonneApercuDTO;
import ci.ubipharm.crm.rest.service.dto.CorrectionLigneDTO;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.ImportDTO;
import ci.ubipharm.crm.rest.service.dto.ImportLigneDTO;
import ci.ubipharm.crm.rest.service.dto.LigneTableurDTO;
import ci.ubipharm.crm.rest.service.dto.ModeleImportDTO;
import ci.ubipharm.crm.rest.service.dto.ParametresImportDTO;
import ci.ubipharm.crm.rest.service.dto.TableurDTO;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;

@Service
public class AssistantImportServiceImpl implements AssistantImportService {

    private static final int NB_EXEMPLES = 3;

    private final Map<String, ImportMetierService> metiers;
    private final LecteurTableurService lecteurTableurService;
    private final CorrespondanceImportService correspondanceImportService;
    private final ImportService importService;
    private final UtilisateurCourantService utilisateurCourantService;

    public AssistantImportServiceImpl(List<ImportMetierService> metiers, LecteurTableurService lecteurTableurService,
            CorrespondanceImportService correspondanceImportService, ImportService importService,
            UtilisateurCourantService utilisateurCourantService) {
        this.metiers = metiers.stream().collect(Collectors.toMap(ImportMetierService::type, Function.identity()));
        this.lecteurTableurService = lecteurTableurService;
        this.correspondanceImportService = correspondanceImportService;
        this.importService = importService;
        this.utilisateurCourantService = utilisateurCourantService;
    }

    @Override
    public List<ChampImportDTO> champs(String type) {
        return metier(type).champs();
    }

    @Override
    public FichierDTO exemplaire(String type) {
        ImportMetierService metier = metier(type);
        List<List<String>> lignes = List.of(
                metier.champs().stream().map(c -> c.getLibelle() + (c.isObligatoire() ? " *" : "")).toList(),
                metier.champs().stream().map(c -> c.getExemple() == null ? "" : c.getExemple()).toList());
        return csv("exemplaire_import_" + metier.type().toLowerCase(Locale.ROOT) + ".csv", lignes);
    }

    @Override
    public ApercuImportDTO apercu(String type, MultipartFile fichier, Long modeleId) {
        ImportMetierService metier = metier(type);
        TableurDTO tableur = lecteurTableurService.lire(fichier);
        List<ColonneApercuDTO> colonnes = IntStream.range(0, tableur.getEntetes().size())
                .filter(i -> !tableur.getEntetes().get(i).isEmpty())
                .mapToObj(i -> ColonneApercuDTO.builder()
                        .cle(tableur.getEntetes().get(i))
                        .libelle(tableur.getEntetesOrigine().get(i))
                        .exemples(exemples(tableur.getLignes(), tableur.getEntetes().get(i)))
                        .build())
                .toList();
        Map<String, String> correspondance = correspondanceImportService.resoudre(metier.type(), metier.champs(), tableur.getEntetes(),
                ParametresImportDTO.builder().modeleId(modeleId).build());
        return ApercuImportDTO.builder()
                .nomFichier(fichier.getOriginalFilename())
                .nbLignes(tableur.getLignes().size())
                .colonnes(colonnes)
                .champs(metier.champs())
                .correspondance(correspondance)
                .champsManquants(correspondanceImportService.champsManquants(metier.champs(), correspondance, tableur.getEntetes()))
                .build();
    }

    @Override
    public List<ModeleImportDTO> modeles(String type) {
        return correspondanceImportService.modeles(metier(type).type());
    }

    @Override
    public ModeleImportDTO enregistrerModele(String type, ModeleImportDTO modele) {
        ImportMetierService metier = metier(type);
        return correspondanceImportService.enregistrerModele(metier.type(), metier.champs(), modele);
    }

    @Override
    public void archiverModele(Long id) {
        correspondanceImportService.archiverModele(id);
    }

    @Override
    public ImportDTO corriger(Long importId, Long ligneId, CorrectionLigneDTO correction) {
        return metier(importService.type(importId)).corriger(importId, ligneId, correction);
    }

    @Override
    public FichierDTO rejets(Long importId) {
        ImportMetierService metier = metier(importService.type(importId));
        List<ChampImportDTO> champs = metier.champs();
        List<List<String>> lignes = new ArrayList<>();
        List<String> entete = new ArrayList<>(List.of("Ligne"));
        champs.forEach(c -> entete.add(c.getLibelle()));
        entete.add("Motif du rejet");
        lignes.add(entete);
        for (ImportLigneDTO rejet : importService.lignesParAction(importId, "REJET")) {
            List<String> ligne = new ArrayList<>(List.of(String.valueOf(rejet.getNumeroLigne())));
            champs.forEach(c -> ligne.add(rejet.getDonnees().getOrDefault(c.getCode(), "")));
            ligne.add(rejet.getMessage());
            lignes.add(ligne);
        }
        return csv("rejets_import_" + importId + ".csv", lignes);
    }

    /** Le type vient de l'URL (clients, factures, produits) ; un import d'un droit ou d'un module non attribue est refuse. */
    private ImportMetierService metier(String type) {
        ImportMetierService metier = metiers.get(type == null ? "" : type.toUpperCase(Locale.ROOT));
        if (metier == null) {
            throw new RessourceIntrouvableException("Type d'import inconnu");
        }
        if (utilisateurCourantService.utilisateur().getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("DROIT_" + metier.droitRequis()))) {
            throw new AccessDeniedException("Droit " + metier.droitRequis() + " non attribue");
        }
        if (metier.moduleRequis() != null && utilisateurCourantService.utilisateur().getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("MODULE_" + metier.moduleRequis()))) {
            throw new AccessDeniedException("Module " + metier.moduleRequis() + " non attribue");
        }
        return metier;
    }

    private List<String> exemples(List<LigneTableurDTO> lignes, String cle) {
        return lignes.stream()
                .map(l -> l.getValeurs().get(cle))
                .filter(v -> v != null && !v.isBlank())
                .limit(NB_EXEMPLES)
                .toList();
    }

    /** CSV lisible par Excel en francais : point-virgule et UTF-8 avec BOM. */

    private FichierDTO csv(String nom, List<List<String>> lignes) {
        return FichierCsv.csv(nom, lignes);
    }
}
