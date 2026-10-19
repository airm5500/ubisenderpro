package ci.ubipharm.crm.rest.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.ArchivageService;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.JournalService;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.JournalCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.JournalLigneDTO;
import ci.ubipharm.crm.rest.service.dto.JournalOptionDTO;
import ci.ubipharm.crm.rest.service.dto.JournalReferentielDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

/**
 * Journal des actions et des connexions, reunis dans une seule liste : qui a fait quoi, quand, sur quelle donnee.
 * Le type de donnee et la nature sont deduits du code d'action (ClassementJournal) ; rien n'est modifie ni supprime ici.
 */
@Service
@RequiredArgsConstructor
public class JournalServiceImpl implements JournalService {

    private static final int EXPORT_MAX = 50_000;
    private static final DateTimeFormatter DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final List<String> ENTETES = List.of("Date", "Utilisateur", "Identifiant", "Nature", "Type de donnée", "Opération",
            "Objet concerné", "Détail", "Adresse IP");

    private static final String TYPE = ClassementJournal.sqlType("j");
    private static final String NATURE = ClassementJournal.sqlNature("j");

    /** Libelle de l'objet concerne, retrouve dans sa table (seulement pour les lignes affichees). */
    private static final String OBJET = """
            (case j.entite
               when 'crm_client' then (select concat(x.code, ' - ', x.raison_sociale) from crm_client x where x.id = j.entite_id)
               when 'crm_facture' then (select concat('Facture ', f.reference, ' - ', x.raison_sociale) from crm_facture f
                                        join crm_client x on x.id = f.client_id where f.id = j.entite_id)
               when 'crm_utilisateur' then (select concat(x.nom_complet, ' (', x.login, ')') from crm_utilisateur x where x.id = j.entite_id)
               when 'crm_campagne' then (select x.nom from crm_campagne x where x.id = j.entite_id)
               when 'crm_promotion' then (select concat(x.code, ' - ', x.nom) from crm_promotion x where x.id = j.entite_id)
               when 'crm_produit' then (select concat(x.code, ' - ', x.libelle) from crm_produit x where x.id = j.entite_id)
               when 'crm_agence' then (select concat(x.code, ' - ', x.libelle) from crm_agence x where x.id = j.entite_id)
               when 'crm_role' then (select x.libelle from crm_role x where x.id = j.entite_id)
               when 'crm_liste_diffusion' then (select x.nom from crm_liste_diffusion x where x.id = j.entite_id)
               when 'crm_modele_message' then (select x.nom from crm_modele_message x where x.id = j.entite_id)
               when 'crm_referentiel' then (select x.libelle from crm_referentiel x where x.id = j.entite_id)
               when 'crm_segment' then (select concat(x.code, ' - ', x.libelle) from crm_segment x where x.id = j.entite_id)
               when 'crm_evenement' then (select x.nom from crm_evenement x where x.id = j.entite_id)
               when 'crm_information' then (select x.titre from crm_information x where x.id = j.entite_id)
               when 'crm_fournisseur_sms' then (select concat(x.code, ' - ', x.libelle) from crm_fournisseur_sms x where x.id = j.entite_id)
               when 'crm_import' then (select x.nom_fichier from crm_import x where x.id = j.entite_id)
               when 'crm_envoi_masse' then (select x.nom from crm_envoi_masse x where x.id = j.entite_id)
               when 'crm_support_ticket' then (select x.sujet from crm_support_ticket x where x.id = j.entite_id)
             end)""";

    /** Client a ouvrir pour une ligne sur un client ou sur l'une de ses factures. */
    private static final String CLIENT = """
            (case j.entite when 'crm_client' then j.entite_id
               when 'crm_facture' then (select f.client_id from crm_facture f where f.id = j.entite_id) end)""";

    /** Ecran de liste pour les donnees sans fiche propre. */
    private static final Map<String, String> ECRANS = Map.ofEntries(
            Map.entry("crm_utilisateur", "/utilisateurs"),
            Map.entry("crm_agence", "/agences"),
            Map.entry("crm_role", "/roles"),
            Map.entry("crm_parametre", "/parametres"),
            Map.entry("crm_charte_fichier", "/parametres"),
            Map.entry("crm_referentiel", "/referentiels"),
            Map.entry("crm_segment", "/referentiels"),
            Map.entry("crm_produit", "/marketing/catalogue"),
            Map.entry("crm_fournisseur_sms", "/fournisseurs-sms"),
            Map.entry("crm_information", "/marketing/informations"),
            Map.entry("crm_evenement", "/marketing/evenements"),
            Map.entry("crm_import", "/imports"),
            Map.entry("crm_delai_relance", "/delais-relance"),
            Map.entry("crm_support_ticket", "/support"));

    private final EntityManager em;
    private final ArchivageService archivageService;
    private final JournalActionService journalActionService;

    @Override
    @Transactional(readOnly = true)
    public PageDTO<JournalLigneDTO> rechercher(JournalCriteresDTO criteres) {
        Map<String, Object> parametres = new LinkedHashMap<>();
        String from = from(criteres, parametres);
        Query comptage = em.createNativeQuery("select count(*)" + from);
        parametres.forEach(comptage::setParameter);
        long total = ((Number) comptage.getSingleResult()).longValue();
        List<JournalLigneDTO> elements = lignes(from, parametres, criteres.getPage() * criteres.getTaille(), criteres.getTaille());
        return PageDTO.<JournalLigneDTO>builder().elements(elements).total(total).page(criteres.getPage()).taille(criteres.getTaille()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public JournalReferentielDTO referentiel() {
        @SuppressWarnings("unchecked")
        List<Tuple> utilisateurs = em.createNativeQuery("""
                select id as id, nom_complet as nom, login as login, statut as statut from crm_utilisateur
                where statut <> 'ARCHIVE' order by nom_complet, login
                """, Tuple.class).getResultList();
        List<JournalOptionDTO> auteurs = new ArrayList<>(utilisateurs.stream().map(this::mapTupleToOption).toList());
        auteurs.add(JournalOptionDTO.builder().code("0").libelle("Traitements automatiques").build());
        return JournalReferentielDTO.builder()
                .types(ClassementJournal.TYPES.stream()
                        .map(t -> JournalOptionDTO.builder().code(t.code()).libelle(t.libelle()).build()).toList())
                .natures(ClassementJournal.NATURES.entrySet().stream()
                        .map(e -> JournalOptionDTO.builder().code(e.getKey()).libelle(e.getValue()).build()).toList())
                .utilisateurs(auteurs)
                .build();
    }

    @Override
    @Transactional
    public FichierDTO exporter(JournalCriteresDTO criteres) {
        List<JournalLigneDTO> lignes = tout(criteres);
        List<List<String>> table = new ArrayList<>();
        table.add(ENTETES);
        lignes.forEach(l -> table.add(colonnes(l)));
        FichierDTO fichier = FichierCsv.csv("journal-actions-" + LocalDateTime.now().format(HORODATAGE) + ".csv", table);
        tracerExport(fichier, lignes.size());
        return fichier;
    }

    @Override
    @Transactional
    public FichierDTO exporterExcel(JournalCriteresDTO criteres) {
        List<JournalLigneDTO> lignes = tout(criteres);
        try (SXSSFWorkbook classeur = new SXSSFWorkbook(500); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            CellStyle gras = classeur.createCellStyle();
            Font police = classeur.createFont();
            police.setBold(true);
            gras.setFont(police);
            Sheet feuille = classeur.createSheet("Journal des actions");
            Row entete = feuille.createRow(0);
            for (int c = 0; c < ENTETES.size(); c++) {
                entete.createCell(c).setCellValue(ENTETES.get(c));
                entete.getCell(c).setCellStyle(gras);
            }
            int numero = 1;
            for (JournalLigneDTO ligne : lignes) {
                Row row = feuille.createRow(numero++);
                List<String> valeurs = colonnes(ligne);
                for (int c = 0; c < valeurs.size(); c++) {
                    // Texte brut : une valeur commencant par = ne doit jamais devenir une formule
                    row.createCell(c).setCellValue(valeurs.get(c) == null ? "" : valeurs.get(c));
                }
            }
            int[] largeurs = {20, 24, 16, 22, 26, 30, 36, 80, 16};
            for (int c = 0; c < largeurs.length; c++) {
                feuille.setColumnWidth(c, largeurs[c] * 256);
            }
            ((SXSSFSheet) feuille).createFreezePane(0, 1);
            feuille.setAutoFilter(new CellRangeAddress(0, Math.max(0, numero - 1), 0, ENTETES.size() - 1));
            classeur.write(sortie);
            FichierDTO fichier = FichierDTO.builder().nom("journal-actions-" + LocalDateTime.now().format(HORODATAGE) + ".xlsx")
                    .typeContenu("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").contenu(sortie.toByteArray()).build();
            tracerExport(fichier, lignes.size());
            return fichier;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<JournalLigneDTO> tout(JournalCriteresDTO criteres) {
        Map<String, Object> parametres = new LinkedHashMap<>();
        return lignes(from(criteres, parametres), parametres, 0, EXPORT_MAX);
    }

    private void tracerExport(FichierDTO fichier, int nbLignes) {
        archivageService.archiver("exports-journal", fichier.getNom(), fichier.getContenu());
        journalActionService.enregistrer("JOURNAL_EXPORT", null, null, fichier.getNom() + " : " + nbLignes + " ligne(s)");
    }

    private List<String> colonnes(JournalLigneDTO l) {
        return List.of(l.getLe() == null ? "" : l.getLe().format(DATE_HEURE),
                l.getUtilisateurNom() != null ? l.getUtilisateurNom() : "systeme".equals(l.getLogin()) ? "Traitement automatique" : texte(l.getLogin()),
                texte(l.getLogin()), l.getNatureLibelle(), l.getTypeLibelle(), l.getActionLibelle(), texte(l.getObjet()), texte(l.getDetails()),
                texte(l.getAdresseIp()));
    }

    private static String texte(String valeur) {
        return valeur == null ? "" : valeur;
    }

    /**
     * Les deux journaux (actions et connexions) ramenes aux memes colonnes. La periode est posee dans chaque branche
     * pour profiter des index de date ; les autres filtres portent sur l'ensemble.
     */
    private String from(JournalCriteresDTO criteres, Map<String, Object> parametres) {
        StringBuilder periodeA = new StringBuilder(" where 1 = 1");
        StringBuilder periodeC = new StringBuilder(" where 1 = 1");
        if (criteres.getDu() != null) {
            periodeA.append(" and a.created_at >= :du");
            periodeC.append(" and c.created_at >= :du");
            parametres.put("du", criteres.getDu().atStartOfDay());
        }
        if (criteres.getAu() != null) {
            periodeA.append(" and a.created_at < :au");
            periodeC.append(" and c.created_at < :au");
            parametres.put("au", criteres.getAu().plusDays(1).atStartOfDay());
        }
        if (criteres.getDu() != null && criteres.getAu() != null && criteres.getAu().isBefore(criteres.getDu())) {
            throw new ErreurMetierException("La date de fin précède la date de début.");
        }
        boolean connexions = criteres.getClientId() == null
                && (vide(criteres.getType()) || "SESSION".equals(criteres.getType()))
                && (vide(criteres.getNature()) || "CONNEXION".equals(criteres.getNature()));
        StringBuilder sql = new StringBuilder(" " + """
                from (select concat('A', a.id) as cle, a.id as tri, a.utilisateur_id, a.login, a.action, a.entite, a.entite_id, a.details,
                              a.adresse_ip, a.created_at
                       from crm_journal_action a""").append(periodeA);
        if (connexions) {
            sql.append("""

                       union all
                       select concat('C', c.id), c.id, c.utilisateur_id, c.login,
                              case when c.succes then 'CONNEXION' else 'CONNEXION_ECHEC' end, 'crm_utilisateur', c.utilisateur_id,
                              c.navigateur, c.adresse_ip, c.created_at
                       from crm_journal_connexion c""").append(periodeC);
        }
        sql.append(") j left join crm_utilisateur u on u.id = j.utilisateur_id where 1 = 1");
        if (criteres.getUtilisateurId() != null) {
            if (criteres.getUtilisateurId() == 0L) {
                sql.append(" and j.utilisateur_id is null");
            } else {
                sql.append(" and j.utilisateur_id = :utilisateurId");
                parametres.put("utilisateurId", criteres.getUtilisateurId());
            }
        }
        if (!vide(criteres.getType())) {
            if (!ClassementJournal.typeConnu(criteres.getType())) {
                throw new ErreurMetierException("Type de donnée inconnu : " + criteres.getType() + ".");
            }
            sql.append(" and ").append(TYPE).append(" = :type");
            parametres.put("type", criteres.getType());
        }
        if (!vide(criteres.getNature())) {
            if (!ClassementJournal.NATURES.containsKey(criteres.getNature())) {
                throw new ErreurMetierException("Nature d'action inconnue : " + criteres.getNature() + ".");
            }
            sql.append(" and ").append(NATURE).append(" = :nature");
            parametres.put("nature", criteres.getNature());
        }
        if (criteres.getClientId() != null) {
            sql.append(" and ((j.entite = 'crm_client' and j.entite_id = :clientId)"
                    + " or (j.entite = 'crm_facture' and j.entite_id in (select f.id from crm_facture f where f.client_id = :clientId)))");
            parametres.put("clientId", criteres.getClientId());
        }
        if (!vide(criteres.getQ())) {
            sql.append(" and (j.details like :q or j.login like :q or u.nom_complet like :q or ").append(OBJET).append(" like :q)");
            parametres.put("q", "%" + criteres.getQ().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        return sql.toString();
    }

    @SuppressWarnings("unchecked")
    private List<JournalLigneDTO> lignes(String from, Map<String, Object> parametres, int debut, int nombre) {
        Query requete = em.createNativeQuery("""
                select j.cle as cle, j.created_at as le, j.utilisateur_id as utilisateurId, j.login as login, u.nom_complet as utilisateurNom,
                       j.action as action, j.entite as entite, j.entite_id as entiteId, j.details as details, j.adresse_ip as adresseIp,
                       """ + TYPE + " as type, " + NATURE + " as nature, " + OBJET + " as objet, " + CLIENT + " as clientId"
                + from + " order by j.created_at desc, j.tri desc", Tuple.class);
        parametres.forEach(requete::setParameter);
        requete.setFirstResult(debut);
        requete.setMaxResults(nombre);
        return ((List<Tuple>) requete.getResultList()).stream().map(this::mapTupleToDto).toList();
    }

    JournalLigneDTO mapTupleToDto(Tuple tuple) {
        String type = tuple.get("type", String.class);
        String nature = tuple.get("nature", String.class);
        String action = tuple.get("action", String.class);
        String entite = tuple.get("entite", String.class);
        Long entiteId = nombre(tuple.get("entiteId"));
        return JournalLigneDTO.builder()
                .cle(tuple.get("cle", String.class))
                .le(tuple.get("le", LocalDateTime.class))
                .utilisateurId(nombre(tuple.get("utilisateurId")))
                .login(tuple.get("login", String.class))
                .utilisateurNom(tuple.get("utilisateurNom", String.class))
                .action(action)
                .actionLibelle(ClassementJournal.libelleAction(action))
                .nature(nature)
                .natureLibelle(ClassementJournal.libelleNature(nature))
                .type(type)
                .typeLibelle(ClassementJournal.libelleType(type))
                .entite(entite)
                .entiteId(entiteId)
                .objet(tuple.get("objet", String.class))
                .lien(lien(type, entite, entiteId, nombre(tuple.get("clientId"))))
                .details(tuple.get("details", String.class))
                .adresseIp(tuple.get("adresseIp", String.class))
                .build();
    }

    JournalOptionDTO mapTupleToOption(Tuple tuple) {
        String nom = tuple.get("nom", String.class);
        return JournalOptionDTO.builder()
                .code(String.valueOf(tuple.get("id", Long.class)))
                .libelle((nom == null ? tuple.get("login", String.class) : nom) + ("ACTIF".equals(tuple.get("statut", String.class)) ? "" : " (désactivé)"))
                .build();
    }

    private static String lien(String type, String entite, Long entiteId, Long clientId) {
        if (clientId != null) {
            return "/clients/" + clientId;
        }
        if (entite == null) {
            return null;
        }
        if (entiteId != null) {
            switch (entite) {
                case "crm_campagne":
                    return ("CAMPAGNE_MARKETING".equals(type) ? "/marketing/campagnes/" : "/campagnes/") + entiteId;
                case "crm_promotion":
                    return "/marketing/promotions/" + entiteId;
                case "crm_liste_diffusion":
                    return "/marketing/listes/" + entiteId;
                default:
                    break;
            }
        }
        if ("crm_modele_message".equals(entite)) {
            return "MODELE".equals(type) ? "/modeles" : null;
        }
        return ECRANS.get(entite);
    }

    private static Long nombre(Object valeur) {
        return valeur instanceof Number n ? n.longValue() : null;
    }

    private static boolean vide(String valeur) {
        return valeur == null || valeur.isBlank();
    }
}
