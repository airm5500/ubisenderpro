package ci.ubipharm.crm.rest.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.FactureService;
import ci.ubipharm.crm.rest.service.PortefeuilleService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.FactureCriteresDTO;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.dto.FactureDTO;
import ci.ubipharm.crm.rest.service.dto.FactureDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.SousTypeDTO;
import ci.ubipharm.crm.rest.service.dto.SuiviFactureDTO;
import ci.ubipharm.crm.rest.service.dto.SuiviFactureDemandeDTO;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FactureServiceImpl implements FactureService {

    private static final String SELECT = """
            select f.id as id, f.reference as reference, c.id as clientId, c.code as clientCode,
                   c.raison_sociale as clientRaisonSociale, a.id as agenceId, a.libelle as agenceLibelle,
                   st.id as sousTypeId, st.libelle as sousTypeLibelle, f.nature as nature,
                   f.date_facture as dateFacture, f.date_echeance as dateEcheance, f.montant as montant, f.solde as solde,
                   f.statut as statut, greatest(datediff(utc_date(), f.date_echeance), 0) as joursRetard,
                   f.suivi as suivi, f.suivi_date as datePromesse, f.suivi_montant as montantPromis, f.suivi_commentaire as suiviCommentaire,
                   us.nom_complet as suiviPar, f.suivi_le as suiviLe
            from crm_facture f
            join crm_client c on c.id = f.client_id
            left join crm_utilisateur us on us.id = f.suivi_par
            join crm_agence a on a.id = c.agence_id
            join crm_sous_type_compte st on st.id = f.sous_type_id
            """;

    private static final String FROM_COMPTAGE = """
            select count(*) from crm_facture f
            join crm_client c on c.id = f.client_id
            """;

    private final EntityManager em;
    private final PerimetreService perimetreService;
    private final PortefeuilleService portefeuilleService;
    private final JournalActionService journalActionService;
    private final UtilisateurCourantService utilisateurCourantService;

    private static final DateTimeFormatter DATE_LISIBLE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    @Transactional(readOnly = true)
    public PageDTO<FactureDTO> rechercher(FactureCriteresDTO criteres) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (!perimetre.isVisionGlobale() && perimetre.getAgenceIds().isEmpty()) {
            return PageDTO.<FactureDTO>builder().elements(List.of()).total(0).page(criteres.getPage()).taille(criteres.getTaille()).build();
        }

        // Le cloisonnement par agence du client est applique dans la requete
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> parametres = new LinkedHashMap<>();
        if (!perimetre.isVisionGlobale()) {
            where.append(" and c.agence_id in (:agencesVisibles)");
            parametres.put("agencesVisibles", perimetre.getAgenceIds());
        }
        List<Long> agentsGeres = portefeuilleService.agentsGeres();
        if (agentsGeres != null) {
            // Un agent ne voit que les factures de son portefeuille et de ceux qu'il remplace
            where.append(" and exists (select 1 from crm_affectation af where af.client_id = c.id and af.date_fin is null"
                    + " and af.agent_id in (:agentsGeres))");
            parametres.put("agentsGeres", agentsGeres);
        }
        if (criteres.getFactureId() != null) {
            where.append(" and f.id = :factureId");
            parametres.put("factureId", criteres.getFactureId());
        }
        if (criteres.getClientId() != null) {
            where.append(" and f.client_id = :clientId");
            parametres.put("clientId", criteres.getClientId());
        }
        if (criteres.getAgenceId() != null) {
            where.append(" and c.agence_id = :agenceId");
            parametres.put("agenceId", criteres.getAgenceId());
        }
        if (criteres.getSousTypeId() != null) {
            where.append(" and f.sous_type_id = :sousTypeId");
            parametres.put("sousTypeId", criteres.getSousTypeId());
        }
        if (criteres.getStatut() != null) {
            where.append(" and f.statut = :statut");
            parametres.put("statut", criteres.getStatut());
        }
        if (criteres.getSuivi() != null) {
            where.append(switch (criteres.getSuivi()) {
                case "PROMESSE" -> " and f.suivi = 'PROMESSE' and f.suivi_date >= utc_date()";
                case "PROMESSE_ECHUE" -> " and f.suivi = 'PROMESSE' and f.suivi_date < utc_date() and f.solde > 0";
                case "AUCUN" -> " and f.suivi is null";
                default -> " and f.suivi = '" + criteres.getSuivi() + "'";
            });
        }
        if (criteres.getNature() != null) {
            where.append(" and f.nature = :nature");
            parametres.put("nature", criteres.getNature());
        }
        if (criteres.getEcheanceDu() != null) {
            where.append(" and f.date_echeance >= :echeanceDu");
            parametres.put("echeanceDu", criteres.getEcheanceDu());
        }
        if (criteres.getEcheanceAu() != null) {
            where.append(" and f.date_echeance <= :echeanceAu");
            parametres.put("echeanceAu", criteres.getEcheanceAu());
        }
        if (criteres.isEnRetard()) {
            where.append(" and f.date_echeance < utc_date() and f.solde <> 0");
        }
        if (criteres.getQ() != null && !criteres.getQ().isBlank()) {
            where.append(" and (f.reference like :q or c.code like :q or c.raison_sociale like :q)");
            parametres.put("q", "%" + echapperLike(criteres.getQ().trim()) + "%");
        }

        Query comptage = em.createNativeQuery(FROM_COMPTAGE + where);
        parametres.forEach(comptage::setParameter);
        long total = ((Number) comptage.getSingleResult()).longValue();

        Query requete = em.createNativeQuery(SELECT + where + " order by f.date_echeance, f.reference", Tuple.class);
        parametres.forEach(requete::setParameter);
        requete.setFirstResult(criteres.getPage() * criteres.getTaille());
        requete.setMaxResults(criteres.getTaille());
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return PageDTO.<FactureDTO>builder()
                .elements(lignes.stream().map(this::mapTupleToDto).toList())
                .total(total)
                .page(criteres.getPage())
                .taille(criteres.getTaille())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SousTypeDTO> sousTypes() {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(
                "select id as id, code as code, libelle as libelle from crm_sous_type_compte where statut = 'ACTIF' order by libelle",
                Tuple.class).getResultList();
        return lignes.stream().map(this::mapTupleToSousTypeDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FactureDTO consulter(Long id) {
        return rechercher(FactureCriteresDTO.builder().factureId(id).taille(1).build()).getElements().stream().findFirst()
                .orElseThrow(() -> new RessourceIntrouvableException("Facture introuvable"));
    }

    @Override
    @Transactional
    public FactureDTO creer(FactureDemandeDTO demande) {
        List<String> motifs = new ArrayList<>();
        String reference = vide(demande.getReference());
        if (reference == null) {
            motifs.add("Saisissez la référence de la facture.");
        } else {
            Number homonymes = (Number) em.createNativeQuery("select count(*) from crm_facture where upper(reference) = upper(:reference)")
                    .setParameter("reference", reference).getSingleResult();
            if (homonymes.longValue() > 0) {
                motifs.add("La facture " + reference + " existe déjà.");
            }
        }
        if (demande.getClientId() == null) {
            motifs.add("Choisissez le client.");
        } else {
            verifierClient(demande.getClientId(), motifs);
        }
        Saisie saisie = verifier(demande, motifs);
        em.createNativeQuery("""
                insert into crm_facture (reference, client_id, sous_type_id, nature, date_facture, date_echeance, montant, solde, statut, created_at)
                values (:reference, :clientId, :sousTypeId, :nature, :dateFacture, :dateEcheance, :montant, :solde,
                        case when :solde = 0 then 'SOLDEE' else 'A_RELANCER' end, utc_timestamp())
                """)
                .setParameter("reference", reference)
                .setParameter("clientId", demande.getClientId())
                .setParameter("sousTypeId", demande.getSousTypeId())
                .setParameter("nature", saisie.nature())
                .setParameter("dateFacture", demande.getDateFacture())
                .setParameter("dateEcheance", demande.getDateEcheance())
                .setParameter("montant", saisie.montant())
                .setParameter("solde", saisie.solde())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        journalActionService.enregistrer("FACTURE_CREATION", "crm_facture", id,
                reference + " : " + saisie.montant().toPlainString() + " (solde " + saisie.solde().toPlainString() + ")");
        return consulter(id);
    }

    @Override
    @Transactional
    public FactureDTO modifier(Long id, FactureDemandeDTO demande) {
        FactureDTO avant = consulter(id);
        List<String> motifs = new ArrayList<>();
        Saisie saisie = verifier(demande, motifs);
        // Meme regle que l'import : solde a zero = soldee ; une facture soldee dont le solde reapparait repasse a relancer
        em.createNativeQuery("""
                update crm_facture set statut = case when :solde = 0 then 'SOLDEE' when statut = 'SOLDEE' then 'A_RELANCER' else statut end,
                       sous_type_id = :sousTypeId, nature = :nature, date_facture = :dateFacture, date_echeance = :dateEcheance,
                       montant = :montant, solde = :solde, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("sousTypeId", demande.getSousTypeId())
                .setParameter("nature", saisie.nature())
                .setParameter("dateFacture", demande.getDateFacture())
                .setParameter("dateEcheance", demande.getDateEcheance())
                .setParameter("montant", saisie.montant())
                .setParameter("solde", saisie.solde())
                .setParameter("id", id)
                .executeUpdate();
        FactureDTO apres = consulter(id);
        List<String> ecarts = new ArrayList<>();
        ecart(ecarts, "Nature", avant.getNature(), apres.getNature());
        ecart(ecarts, "Sous-type", avant.getSousTypeLibelle(), apres.getSousTypeLibelle());
        ecart(ecarts, "Date de facture", avant.getDateFacture().format(DATE_LISIBLE), apres.getDateFacture().format(DATE_LISIBLE));
        ecart(ecarts, "Échéance", avant.getDateEcheance().format(DATE_LISIBLE), apres.getDateEcheance().format(DATE_LISIBLE));
        ecart(ecarts, "Montant", avant.getMontant().toPlainString(), apres.getMontant().toPlainString());
        ecart(ecarts, "Solde", avant.getSolde().toPlainString(), apres.getSolde().toPlainString());
        if (!ecarts.isEmpty()) {
            journalActionService.enregistrer("FACTURE_MODIFICATION", "crm_facture", id, avant.getReference() + " : " + String.join(" ; ", ecarts));
        }
        return apres;
    }

    @Override
    @Transactional
    public FactureDTO poserSuivi(Long id, SuiviFactureDemandeDTO demande) {
        FactureDTO facture = consulter(id);
        String suivi = demande.getSuivi();
        String commentaire = vide(demande.getCommentaire());
        if (("CONTENTIEUX".equals(suivi) || "CONTENTIEUX".equals(facture.getSuivi())) && !aLeDroit("DROIT_FACTURES_CONTENTIEUX")) {
            throw new ErreurMetierException("Seule la Direction du Recouvrement passe une facture au contentieux ou l'en sort.");
        }
        if (suivi == null && facture.getSuivi() == null) {
            throw new ErreurMetierException("La facture n'a ni promesse, ni litige, ni contentieux à lever.");
        }
        if (suivi != null && ("AVOIR".equals(facture.getNature()) || facture.getSolde().signum() <= 0)) {
            throw new ErreurMetierException("La facture " + facture.getReference() + " n'a plus de solde à recouvrer.");
        }
        LocalDate date = null;
        BigDecimal montant = null;
        if ("PROMESSE".equals(suivi)) {
            date = demande.getDatePromesse();
            if (date == null) {
                throw new ErreurMetierException("Indiquez la date à laquelle le client promet de payer.");
            }
            if (date.isBefore(LocalDate.now(ZoneOffset.UTC))) {
                throw new ErreurMetierException("La date promise est déjà passée.");
            }
            montant = demande.getMontantPromis();
            if (montant != null && (montant.signum() <= 0 || montant.compareTo(facture.getSolde()) > 0)) {
                throw new ErreurMetierException("Le montant promis doit être positif et ne pas dépasser le solde (" + facture.getSolde().toPlainString() + ").");
            }
        } else if (suivi != null && commentaire == null) {
            throw new ErreurMetierException("LITIGE".equals(suivi) ? "Décrivez le litige (motif du client)." : "Indiquez le motif du passage au contentieux.");
        }
        Long utilisateur = utilisateurCourantService.utilisateur().getId();
        em.createNativeQuery("""
                update crm_facture set suivi = :suivi, suivi_date = :date, suivi_montant = :montant, suivi_commentaire = :commentaire,
                       suivi_par = :par, suivi_le = utc_timestamp(), updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("suivi", suivi).setParameter("date", date).setParameter("montant", montant)
                .setParameter("commentaire", commentaire).setParameter("par", utilisateur).setParameter("id", id)
                .executeUpdate();
        em.createNativeQuery("""
                insert into crm_facture_suivi (facture_id, suivi, suivi_date, suivi_montant, commentaire, cree_par, created_at)
                values (:id, :suivi, :date, :montant, :commentaire, :par, utc_timestamp())
                """)
                .setParameter("suivi", suivi).setParameter("date", date).setParameter("montant", montant)
                .setParameter("commentaire", commentaire).setParameter("par", utilisateur).setParameter("id", id)
                .executeUpdate();
        String details = facture.getReference() + " : " + switch (suivi == null ? "LEVEE" : suivi) {
            case "PROMESSE" -> "promesse de paiement au " + date.format(DATE_LISIBLE) + (montant == null ? "" : " (" + montant.toPlainString() + ")");
            case "LITIGE" -> "litige";
            case "CONTENTIEUX" -> "contentieux";
            default -> "suivi leve (" + facture.getSuivi() + ")";
        } + (commentaire == null ? "" : " - " + commentaire);
        journalActionService.enregistrer("FACTURE_SUIVI", "crm_facture", id, details);
        return consulter(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SuiviFactureDTO> historiqueSuivi(Long id) {
        consulter(id);
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select s.id as id, s.suivi as suivi, s.suivi_date as datePromesse, s.suivi_montant as montantPromis, s.commentaire as commentaire,
                       u.nom_complet as par, s.created_at as le
                from crm_facture_suivi s left join crm_utilisateur u on u.id = s.cree_par
                where s.facture_id = :id order by s.created_at desc, s.id desc
                """, Tuple.class).setParameter("id", id).getResultList();
        return lignes.stream().map(this::mapTupleToSuiviDto).toList();
    }

    private boolean aLeDroit(String autorite) {
        return utilisateurCourantService.utilisateur().getAuthorities().stream().anyMatch(a -> autorite.equals(a.getAuthority()));
    }

    private record Saisie(String nature, BigDecimal montant, BigDecimal solde) {
    }

    /** Memes controles que l'import des factures. */
    private Saisie verifier(FactureDemandeDTO demande, List<String> motifs) {
        Number sousType = (Number) em.createNativeQuery("select count(*) from crm_sous_type_compte where id = :id and statut = 'ACTIF'")
                .setParameter("id", demande.getSousTypeId()).getSingleResult();
        if (sousType.longValue() == 0) {
            motifs.add("Sous-type inconnu.");
        }
        if (demande.getDateEcheance().isBefore(demande.getDateFacture())) {
            motifs.add("L'échéance (" + demande.getDateEcheance().format(DATE_LISIBLE) + ") précède la date de facture ("
                    + demande.getDateFacture().format(DATE_LISIBLE) + ").");
        }
        String nature = demande.getNature() == null || demande.getNature().isBlank() ? "FACTURE" : demande.getNature();
        if (!"FACTURE".equals(nature) && !"AVOIR".equals(nature)) {
            motifs.add("Nature inconnue : " + nature + ".");
        }
        BigDecimal montant = demande.getMontant().abs().setScale(2, RoundingMode.HALF_UP);
        BigDecimal solde = demande.getSolde().abs().setScale(2, RoundingMode.HALF_UP);
        if (montant.signum() == 0) {
            motifs.add("Le montant ne peut pas être nul.");
        }
        if (solde.compareTo(montant) > 0) {
            motifs.add("Le solde (" + solde.toPlainString() + ") dépasse le montant (" + montant.toPlainString() + ").");
        }
        if (!motifs.isEmpty()) {
            throw new ErreurMetierException(String.join(" ", motifs));
        }
        return "AVOIR".equals(nature) ? new Saisie(nature, montant.negate(), solde.negate()) : new Saisie(nature, montant, solde);
    }

    private void verifierClient(Long clientId, List<String> motifs) {
        @SuppressWarnings("unchecked")
        List<Tuple> clients = em.createNativeQuery("select agence_id as agenceId, statut as statut from crm_client where id = :id", Tuple.class)
                .setParameter("id", clientId).getResultList();
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (clients.isEmpty()) {
            motifs.add("Client inconnu.");
        } else if (!perimetre.isVisionGlobale() && !perimetre.getAgenceIds().contains(clients.getFirst().get("agenceId", Long.class))) {
            motifs.add("Ce client est hors de votre périmètre.");
        } else if ("ARCHIVE".equals(clients.getFirst().get("statut", String.class))) {
            motifs.add("Ce client est archivé.");
        } else if (portefeuilleService.agentsGeres() != null) {
            Number affecte = (Number) em.createNativeQuery(
                    "select count(*) from crm_affectation where client_id = :id and date_fin is null and agent_id in (:agents)")
                    .setParameter("id", clientId).setParameter("agents", portefeuilleService.agentsGeres()).getSingleResult();
            if (affecte.longValue() == 0) {
                motifs.add("Ce client n'est pas dans votre portefeuille.");
            }
        }
    }

    private static void ecart(List<String> ecarts, String champ, String avant, String apres) {
        if (!Objects.equals(avant, apres)) {
            ecarts.add(champ + " : " + avant + " -> " + apres);
        }
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    FactureDTO mapTupleToDto(Tuple tuple) {
        return FactureDTO.builder()
                .id(tuple.get("id", Long.class))
                .reference(tuple.get("reference", String.class))
                .clientId(tuple.get("clientId", Long.class))
                .clientCode(tuple.get("clientCode", String.class))
                .clientRaisonSociale(tuple.get("clientRaisonSociale", String.class))
                .agenceId(tuple.get("agenceId", Long.class))
                .agenceLibelle(tuple.get("agenceLibelle", String.class))
                .sousTypeId(tuple.get("sousTypeId", Long.class))
                .sousTypeLibelle(tuple.get("sousTypeLibelle", String.class))
                .nature(tuple.get("nature", String.class))
                .dateFacture(tuple.get("dateFacture", LocalDate.class))
                .dateEcheance(tuple.get("dateEcheance", LocalDate.class))
                .montant(tuple.get("montant", BigDecimal.class))
                .solde(tuple.get("solde", BigDecimal.class))
                .statut(tuple.get("statut", String.class))
                .joursRetard(((Number) tuple.get("joursRetard")).intValue())
                .suivi(tuple.get("suivi", String.class))
                .datePromesse(tuple.get("datePromesse", LocalDate.class))
                .montantPromis(tuple.get("montantPromis", BigDecimal.class))
                .suiviCommentaire(tuple.get("suiviCommentaire", String.class))
                .suiviPar(tuple.get("suiviPar", String.class))
                .suiviLe(tuple.get("suiviLe", LocalDateTime.class))
                .relancesSuspendues(suspendue(tuple.get("suivi", String.class), tuple.get("datePromesse", LocalDate.class)))
                .build();
    }

    private static boolean suspendue(String suivi, LocalDate datePromesse) {
        return "LITIGE".equals(suivi) || "CONTENTIEUX".equals(suivi)
                || "PROMESSE".equals(suivi) && !datePromesse.isBefore(LocalDate.now(ZoneOffset.UTC));
    }

    SuiviFactureDTO mapTupleToSuiviDto(Tuple tuple) {
        return SuiviFactureDTO.builder()
                .id(tuple.get("id", Long.class))
                .suivi(tuple.get("suivi", String.class))
                .datePromesse(tuple.get("datePromesse", LocalDate.class))
                .montantPromis(tuple.get("montantPromis", BigDecimal.class))
                .commentaire(tuple.get("commentaire", String.class))
                .par(tuple.get("par", String.class))
                .le(tuple.get("le", LocalDateTime.class))
                .build();
    }

    SousTypeDTO mapTupleToSousTypeDto(Tuple tuple) {
        return SousTypeDTO.builder()
                .id(tuple.get("id", Long.class))
                .code(tuple.get("code", String.class))
                .libelle(tuple.get("libelle", String.class))
                .build();
    }

    private String echapperLike(String texte) {
        return texte.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
