package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.CampagneMarketingService;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.ListeDiffusionService;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.PieceJointeService;
import ci.ubipharm.crm.rest.service.PromotionService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.VariableMessageService;
import ci.ubipharm.crm.rest.service.dto.CampagneCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.CampagneMarketingDTO;
import ci.ubipharm.crm.rest.service.dto.CampagneMarketingDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.ListeDiffusionDTO;
import ci.ubipharm.crm.rest.service.dto.MessageCampagneCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.MessageCampagneDTO;
import ci.ubipharm.crm.rest.service.dto.MotifDTO;
import ci.ubipharm.crm.rest.service.dto.MotifExclusionDTO;
import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.rest.service.dto.RecapitulatifMarketingDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

/**
 * Campagnes promotionnelles : chaque client joignable de la liste recoit un message par numero retenu par la liste
 * (WhatsApp ou SMS) ou un e-mail. Le WhatsApp part du numero de l'auteur, l'e-mail de l'adresse d'UBIPHARM avec
 * l'adresse de l'auteur en reponse. Les messages sont ceux des relances : la meme chaine d'envoi les expedie.
 */
@Service
@RequiredArgsConstructor
public class CampagneMarketingServiceImpl implements CampagneMarketingService {

    private static final String VALIDEURS = "DROIT_CAMPAGNES_MARKETING_VALIDER";
    private static final Set<String> NON_VALIDEES = Set.of("BROUILLON", "A_VALIDER");
    private static final String EXCLU = "EXCLU";
    private static final String A_ENVOYER = "A_ENVOYER";
    private static final int EXEMPLES = 3;
    private static final Pattern COLONNE = Pattern.compile("[a-z][a-z0-9_]*");

    private static final String SELECT = """
            select cp.id as id, cp.nom as nom, cp.liste_id as listeId, l.nom as listeNom, cp.modele_id as modeleId, mo.nom as modeleNom,
                   mo.canal as canal, mo.objet as modeleObjet, mo.contenu as modeleContenu, mo.statut as modeleStatut,
                   cp.promotion_ids as promotionIds, cp.affiche_id as afficheId, pa.nom_fichier as afficheNom, pa.type_mime as afficheType,
                   cp.joindre_produits as joindreProduits, cp.liste_produits_id as listeProduitsId,
                   (select group_concat(concat(p.code, ' · ', p.nom) order by p.code separator '|') from crm_promotion p
                    where find_in_set(p.id, cp.promotion_ids) > 0) as promotionsLibelles,
                   cp.date_execution as dateExecution, cp.recurrence as recurrence, cp.fin_recurrence as finRecurrence,
                   cp.debut_recurrence as debutRecurrence, cp.nb_executions as nbExecutions, cp.statut as statut, cp.perimetre_agences as perimetreAgences,
                   cp.agences_cibles as agencesCibles, cp.nb_clients as nbClients, cp.nb_messages as nbMessages, cp.nb_exclus as nbExclus,
                   cp.nb_envoyes as nbEnvoyes, cp.nb_echecs as nbEchecs, cp.cree_par as creeParId, uc.nom_complet as creePar,
                   uc.numero_whatsapp as numeroExpediteur, uc.email as emailAuteur, uc.statut as statutAuteur,
                   cp.created_at as createdAt, cp.soumise_le as soumiseLe, uv.nom_complet as validePar, cp.valide_le as valideLe,
                   cp.motif_rejet as motifRejet, ur.nom_complet as rejetePar, cp.lance_le as lanceLe, cp.termine_le as termineLe,
                   ua.nom_complet as annulePar, cp.annule_le as annuleLe, cp.motif_annulation as motifAnnulation
            from crm_campagne cp
            join crm_modele_message mo on mo.id = cp.modele_id
            join crm_liste_diffusion l on l.id = cp.liste_id
            left join crm_piece_jointe pa on pa.id = cp.affiche_id
            join crm_utilisateur uc on uc.id = cp.cree_par
            left join crm_utilisateur uv on uv.id = cp.valide_par
            left join crm_utilisateur ur on ur.id = cp.rejete_par
            left join crm_utilisateur ua on ua.id = cp.annule_par
            """;

    private static final String SELECT_MESSAGES = """
            select m.id as id, m.client_id as clientId, c.code as clientCode, c.raison_sociale as clientRaisonSociale,
                   c.agence_id as agenceId, m.canal as canal, m.numero_destinataire as numeroDestinataire, m.email_destinataire as emailDestinataire,
                   m.numero_expediteur as numeroExpediteur, m.email_reponse as emailReponse, m.objet as objet, m.contenu as contenu,
                   m.statut as statut, m.motif as motif, m.envoye_le as envoyeLe, m.distribue_le as distribueLe, m.lu_le as luLe
            from crm_campagne_message m
            join crm_client c on c.id = m.client_id
            where m.campagne_id = :id
            order by c.raison_sociale, c.code, m.id
            """;

    private final EntityManager em;
    private final NamedParameterJdbcTemplate jdbc;
    private final ListeDiffusionService listeDiffusionService;
    private final VariableMessageService variableMessageService;
    private final PromotionService promotionService;
    private final PieceJointeService pieceJointeService;
    private final PerimetreService perimetreService;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;

    /** Campagne lue en base, avec son modele, son auteur et le perimetre fige a la creation (null = toutes les agences). */
    private record Campagne(CampagneMarketingDTO dto, String objet, String contenu, String modeleStatut, String emailAuteur,
                            boolean auteurActif, List<Long> perimetre, LocalDateTime debutRecurrence) {
    }

    /** Message genere et son contact destinataire. */
    private record Ligne(MessageCampagneDTO message, Long contactId) {

        boolean envoyable() {
            return A_ENVOYER.equals(message.getStatut());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageDTO<CampagneMarketingDTO> rechercher(CampagneCriteresDTO criteres) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> parametres = new LinkedHashMap<>();
        restreindre(where, parametres);
        if (criteres.getStatut() != null) {
            where.append(" and cp.statut = :statut");
            parametres.put("statut", criteres.getStatut());
        }
        if (criteres.getQ() != null && !criteres.getQ().isBlank()) {
            where.append(" and cp.nom like :q");
            parametres.put("q", "%" + criteres.getQ().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        Query comptage = em.createNativeQuery("select count(*) from crm_campagne cp" + where);
        parametres.forEach(comptage::setParameter);
        long total = ((Number) comptage.getSingleResult()).longValue();

        Query requete = em.createNativeQuery(SELECT + where + " order by cp.created_at desc, cp.id desc", Tuple.class);
        parametres.forEach(requete::setParameter);
        requete.setFirstResult(criteres.getPage() * criteres.getTaille());
        requete.setMaxResults(criteres.getTaille());
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return PageDTO.<CampagneMarketingDTO>builder()
                .elements(lignes.stream().map(t -> mapTupleToCampagne(t).dto()).toList())
                .total(total)
                .page(criteres.getPage())
                .taille(criteres.getTaille())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CampagneMarketingDTO consulter(Long id) {
        return lire(id, false).dto();
    }

    @Override
    @Transactional
    public CampagneMarketingDTO creer(CampagneMarketingDemandeDTO demande) {
        verifierDemande(demande);
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        em.createNativeQuery("""
                insert into crm_campagne (module, nom, type_campagne, modele_id, liste_id, promotion_ids, affiche_id, joindre_produits, criteres,
                                          date_execution, recurrence, fin_recurrence, perimetre_agences, cree_par, created_at)
                values ('MARKETING', :nom, 'PROMOTION', :modeleId, :listeId, :promotionIds, :afficheId, :joindreProduits, '{}', :dateExecution,
                        :recurrence, :finRecurrence, :perimetreAgences, :creePar, utc_timestamp())
                """)
                .setParameter("afficheId", demande.getAfficheId())
                .setParameter("joindreProduits", demande.isJoindreProduits())
                .setParameter("recurrence", demande.getRecurrence())
                .setParameter("finRecurrence", demande.getFinRecurrence())
                .setParameter("promotionIds", joindre(promotions(demande)))
                .setParameter("nom", demande.getNom().trim())
                .setParameter("modeleId", demande.getModeleId())
                .setParameter("listeId", demande.getListeId())
                .setParameter("dateExecution", demande.getDateExecution())
                .setParameter("perimetreAgences", perimetre.isVisionGlobale() ? null : joindre(perimetre.getAgenceIds()))
                .setParameter("creePar", perimetre.getUtilisateurId())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        journalActionService.enregistrer("CAMPAGNE_MARKETING_CREATION", "crm_campagne", id, demande.getNom().trim());
        return consulter(id);
    }

    @Override
    @Transactional
    public CampagneMarketingDTO modifier(Long id, CampagneMarketingDemandeDTO demande) {
        CampagneMarketingDTO campagne = lire(id, true).dto();
        verifierAuteurOuValideur(campagne);
        if (!NON_VALIDEES.contains(campagne.getStatut())) {
            throw new ErreurMetierException("Une campagne validée ne se modifie plus ; annulez-la et créez-en une autre.");
        }
        verifierDemande(demande);
        em.createNativeQuery("""
                update crm_campagne set nom = :nom, modele_id = :modeleId, liste_id = :listeId, promotion_ids = :promotionIds,
                       affiche_id = :afficheId, joindre_produits = :joindreProduits,
                       date_execution = :dateExecution, recurrence = :recurrence, fin_recurrence = :finRecurrence, statut = 'BROUILLON',
                       soumise_le = null, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("recurrence", demande.getRecurrence())
                .setParameter("finRecurrence", demande.getFinRecurrence())
                .setParameter("afficheId", demande.getAfficheId())
                .setParameter("joindreProduits", demande.isJoindreProduits())
                .setParameter("promotionIds", joindre(promotions(demande)))
                .setParameter("nom", demande.getNom().trim())
                .setParameter("modeleId", demande.getModeleId())
                .setParameter("listeId", demande.getListeId())
                .setParameter("dateExecution", demande.getDateExecution())
                .setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("CAMPAGNE_MARKETING_MODIFICATION", "crm_campagne", id, demande.getNom().trim());
        return consulter(id);
    }

    @Override
    @Transactional(readOnly = true)
    public RecapitulatifMarketingDTO recapitulatif(Long id) {
        Campagne campagne = lire(id, false);
        List<MessageCampagneDTO> messages = visibles(tousLesMessages(campagne));
        List<MessageCampagneDTO> envoyables = messages.stream().filter(m -> !EXCLU.equals(m.getStatut()) && !"ANNULE".equals(m.getStatut())).toList();
        List<MessageCampagneDTO> exclus = messages.stream().filter(m -> EXCLU.equals(m.getStatut())).toList();
        return RecapitulatifMarketingDTO.builder()
                .nbClients(envoyables.stream().map(MessageCampagneDTO::getClientId).distinct().count())
                .nbMessages(envoyables.size())
                .nbExclus(exclus.size())
                .expediteur(campagne.dto().getCreePar())
                .numeroExpediteur(campagne.dto().getNumeroExpediteur())
                .emailReponse(campagne.emailAuteur())
                .exclusions(exclus.stream().collect(Collectors.groupingBy(MessageCampagneDTO::getMotif, Collectors.counting()))
                        .entrySet().stream()
                        .map(e -> MotifExclusionDTO.builder().motif(e.getKey()).nb(e.getValue()).build())
                        .sorted(Comparator.comparingLong(MotifExclusionDTO::getNb).reversed().thenComparing(MotifExclusionDTO::getMotif))
                        .toList())
                .exemples(envoyables.stream().limit(EXEMPLES).toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageDTO<MessageCampagneDTO> messages(Long id, MessageCampagneCriteresDTO criteres) {
        Predicate<MessageCampagneDTO> filtre = m -> true;
        if (criteres.getStatut() != null) {
            filtre = filtre.and(m -> criteres.getStatut().equals(m.getStatut()));
        }
        if (criteres.getQ() != null && !criteres.getQ().isBlank()) {
            String q = criteres.getQ().trim().toLowerCase();
            filtre = filtre.and(m -> contient(m.getClientCode(), q) || contient(m.getClientRaisonSociale(), q)
                    || contient(m.getNumeroDestinataire(), q) || contient(m.getEmailDestinataire(), q));
        }
        List<MessageCampagneDTO> retenus = visibles(tousLesMessages(lire(id, false))).stream().filter(filtre).toList();
        int debut = Math.min(criteres.getPage() * criteres.getTaille(), retenus.size());
        return PageDTO.<MessageCampagneDTO>builder()
                .elements(retenus.subList(debut, Math.min(debut + criteres.getTaille(), retenus.size())))
                .total(retenus.size())
                .page(criteres.getPage())
                .taille(criteres.getTaille())
                .build();
    }

    @Override
    @Transactional
    public CampagneMarketingDTO soumettre(Long id) {
        Campagne campagne = lire(id, true);
        verifierAuteurOuValideur(campagne.dto());
        if (!"BROUILLON".equals(campagne.dto().getStatut())) {
            throw new ErreurMetierException("Seul un brouillon peut être soumis à validation.");
        }
        verifierExpediteur(campagne);
        verifierPromotions(campagne);
        List<Ligne> lignes = generer(campagne);
        long envoyables = lignes.stream().filter(Ligne::envoyable).count();
        if (envoyables == 0) {
            throw new ErreurMetierException("La campagne ne contient aucun message à envoyer.");
        }
        em.createNativeQuery("update crm_campagne set statut = 'A_VALIDER', soumise_le = utc_timestamp(), motif_rejet = null, rejete_par = null, "
                + "updated_at = utc_timestamp() where id = :id").setParameter("id", id).executeUpdate();
        enregistrerComptes(id, lignes);
        journalActionService.enregistrer("CAMPAGNE_MARKETING_SOUMISSION", "crm_campagne", id, envoyables + " message(s)");
        return consulter(id);
    }

    @Override
    @Transactional
    public CampagneMarketingDTO valider(Long id) {
        Campagne campagne = lire(id, true);
        if (!"A_VALIDER".equals(campagne.dto().getStatut())) {
            throw new ErreurMetierException("Seule une campagne soumise peut être validée.");
        }
        if (!"ACTIF".equals(campagne.modeleStatut())) {
            throw new ErreurMetierException("Le modèle « " + campagne.dto().getModeleNom() + " » a été archivé ; choisissez-en un autre.");
        }
        verifierExpediteur(campagne);
        verifierPromotions(campagne);
        List<Ligne> lignes = generer(campagne);
        if (lignes.stream().noneMatch(Ligne::envoyable)) {
            throw new ErreurMetierException("La campagne ne contient plus aucun message à envoyer.");
        }
        verifierAgencesDuValideur(lignes);
        enregistrerMessages(id, lignes, 1);
        em.createNativeQuery("update crm_campagne set statut = 'VALIDEE', valide_par = :validePar, valide_le = utc_timestamp(), "
                + "updated_at = utc_timestamp() where id = :id")
                .setParameter("validePar", utilisateurCourantService.utilisateur().getId())
                .setParameter("id", id)
                .executeUpdate();
        enregistrerComptes(id, lignes);
        journalActionService.enregistrer("CAMPAGNE_MARKETING_VALIDATION", "crm_campagne", id,
                lignes.stream().filter(Ligne::envoyable).count() + " message(s)");
        joindreListeProduits(campagne);

        LocalDateTime execution = campagne.dto().getDateExecution();
        if (execution == null || !execution.isAfter(maintenant())) {
            demarrer(id, false);
        }
        return consulter(id);
    }

    @Override
    @Transactional
    public CampagneMarketingDTO rejeter(Long id, MotifDTO motif) {
        if (!"A_VALIDER".equals(lire(id, true).dto().getStatut())) {
            throw new ErreurMetierException("Seule une campagne soumise peut être rejetée.");
        }
        em.createNativeQuery("""
                update crm_campagne set statut = 'BROUILLON', motif_rejet = :motif, rejete_par = :rejetePar, soumise_le = null,
                       updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("motif", motif.getMotif().trim())
                .setParameter("rejetePar", utilisateurCourantService.utilisateur().getId())
                .setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("CAMPAGNE_MARKETING_REJET", "crm_campagne", id, motif.getMotif().trim());
        return consulter(id);
    }

    @Override
    @Transactional
    public CampagneMarketingDTO lancer(Long id) {
        if (!"VALIDEE".equals(lire(id, true).dto().getStatut())) {
            throw new ErreurMetierException("Seule une campagne validée et pas encore démarrée peut être lancée.");
        }
        em.createNativeQuery("update crm_campagne set date_execution = utc_timestamp() where id = :id").setParameter("id", id).executeUpdate();
        demarrer(id, false);
        return consulter(id);
    }

    @Override
    @Transactional
    public CampagneMarketingDTO annuler(Long id, MotifDTO motif) {
        CampagneMarketingDTO campagne = lire(id, true).dto();
        verifierAuteurOuValideur(campagne);
        if ("TERMINEE".equals(campagne.getStatut()) || "ANNULEE".equals(campagne.getStatut())) {
            throw new ErreurMetierException("La campagne est déjà " + ("TERMINEE".equals(campagne.getStatut()) ? "terminée." : "annulée."));
        }
        if (!NON_VALIDEES.contains(campagne.getStatut()) && !estValideur()) {
            throw new ErreurMetierException("Une campagne validée ne peut être arrêtée que par une personne qui valide les campagnes.");
        }
        em.createNativeQuery("update crm_campagne_message set statut = 'ANNULE', motif = 'Campagne annulee', updated_at = utc_timestamp() "
                + "where campagne_id = :id and statut = 'A_ENVOYER'").setParameter("id", id).executeUpdate();
        em.createNativeQuery("""
                update crm_campagne set statut = 'ANNULEE', annule_par = :annulePar, annule_le = utc_timestamp(), motif_annulation = :motif,
                       updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("annulePar", utilisateurCourantService.utilisateur().getId())
                .setParameter("motif", motif.getMotif().trim())
                .setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("CAMPAGNE_MARKETING_ANNULATION", "crm_campagne", id, motif.getMotif().trim());
        return consulter(id);
    }

    @Override
    @Transactional
    public int demarrerCampagnesEchues() {
        @SuppressWarnings("unchecked")
        List<Long> ids = em.createNativeQuery("select id from crm_campagne where module = 'MARKETING' and statut = 'VALIDEE' "
                + "and date_execution <= utc_timestamp() order by date_execution for update").getResultList();
        ids.forEach(id -> demarrer(id, true));
        return ids.size();
    }

    /**
     * Passe la campagne en cours ; un client desabonne depuis la validation ne recoit rien. Une campagne recurrente
     * regenere ses messages a chaque envoi apres le premier (liste et promotions du moment), puis attend le suivant ;
     * elle s'arrete d'elle-meme si une promotion citee est finie ou si son auteur ne peut plus envoyer.
     */
    private void demarrer(Long id, boolean systeme) {
        Campagne campagne = lireSansRestriction(id);
        CampagneMarketingDTO dto = campagne.dto();
        int execution = dto.getNbExecutions() + 1;
        if (execution > 1) {
            try {
                if (!"ACTIF".equals(campagne.modeleStatut())) {
                    throw new ErreurMetierException("Le modèle « " + dto.getModeleNom() + " » a été archivé.");
                }
                verifierExpediteur(campagne);
                verifierPromotions(campagne);
            } catch (ErreurMetierException e) {
                arreter(id, e.getMessage() + " Répétition arrêtée.");
                return;
            }
            enregistrerMessages(id, generer(campagne), execution);
            recompterCumul(id);
            joindreListeProduits(campagne);
        }
        int annules = em.createNativeQuery("""
                update crm_campagne_message m join crm_client c on c.id = m.client_id
                set m.statut = 'ANNULE', m.motif = 'Client desabonne avant l''envoi', m.updated_at = utc_timestamp()
                where m.campagne_id = :id and m.statut = 'A_ENVOYER' and (c.desabonne_marketing_le is not null or c.statut <> 'ACTIF')
                """).setParameter("id", id).executeUpdate();
        long restants = ((Number) em.createNativeQuery("select count(*) from crm_campagne_message where campagne_id = :id and statut = 'A_ENVOYER'")
                .setParameter("id", id).getSingleResult()).longValue();
        LocalDateTime debut = campagne.debutRecurrence() != null ? campagne.debutRecurrence()
                : dto.getDateExecution() != null ? dto.getDateExecution() : maintenant();
        LocalDateTime prochaine = dto.getRecurrence() == null ? null
                : Recurrence.prochaine(debut, dto.getRecurrence(), dto.getFinRecurrence(), maintenant());
        String statut = restants > 0 ? "EN_COURS" : prochaine != null ? "VALIDEE" : "TERMINEE";
        em.createNativeQuery("""
                update crm_campagne set statut = :statut, lance_le = utc_timestamp(),
                       termine_le = case when :statut = 'TERMINEE' then utc_timestamp() end,
                       date_execution = coalesce(:prochaine, date_execution),
                       debut_recurrence = case when recurrence is not null then coalesce(debut_recurrence, :debut) end,
                       nb_executions = nb_executions + 1, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("statut", statut)
                .setParameter("prochaine", prochaine)
                .setParameter("debut", debut)
                .setParameter("id", id)
                .executeUpdate();
        String details = (execution > 1 ? "Envoi n° " + execution + " : " : "") + restants + " message(s) a envoyer"
                + (annules > 0 ? ", " + annules + " client(s) desabonne(s) retire(s)" : "");
        if (systeme) {
            journalActionService.enregistrerSysteme("CAMPAGNE_MARKETING_LANCEMENT", "crm_campagne", id, details);
        } else {
            journalActionService.enregistrer("CAMPAGNE_MARKETING_LANCEMENT", "crm_campagne", id, details);
        }
    }

    /** Liste Excel des produits des promotions citees, figee pour l'envoi qui commence. */
    private void joindreListeProduits(Campagne campagne) {
        CampagneMarketingDTO dto = campagne.dto();
        if (!dto.isJoindreProduits() || dto.getPromotionIds().isEmpty()) {
            return;
        }
        Long liste = pieceJointeService.enregistrerGenere("LISTE_PRODUITS", promotionService.classeurCampagne(dto.getPromotionIds()));
        em.createNativeQuery("update crm_campagne set liste_produits_id = :liste where id = :id")
                .setParameter("liste", liste).setParameter("id", dto.getId()).executeUpdate();
    }

    /** Arret d'une campagne recurrente qui ne peut plus repartir. */
    private void arreter(Long id, String motif) {
        em.createNativeQuery("""
                update crm_campagne set statut = 'ANNULEE', annule_le = utc_timestamp(), motif_annulation = :motif, updated_at = utc_timestamp()
                where id = :id
                """).setParameter("motif", motif).setParameter("id", id).executeUpdate();
        journalActionService.enregistrerSysteme("CAMPAGNE_MARKETING_ANNULATION", "crm_campagne", id, motif);
    }

    /** Compteurs d'une campagne recurrente : tous ses envois confondus. */
    private void recompterCumul(Long id) {
        em.createNativeQuery("""
                update crm_campagne c set
                    nb_messages = (select count(*) from crm_campagne_message m where m.campagne_id = c.id and m.statut <> 'EXCLU'),
                    nb_clients = (select count(distinct m.client_id) from crm_campagne_message m where m.campagne_id = c.id and m.statut <> 'EXCLU'),
                    nb_exclus = (select count(*) from crm_campagne_message m where m.campagne_id = c.id and m.statut = 'EXCLU')
                where c.id = :id
                """).setParameter("id", id).executeUpdate();
    }

    /** Fige les messages d'un envoi. */
    private void enregistrerMessages(Long id, List<Ligne> lignes, int execution) {
        SqlParameterSource[] lots = lignes.stream().map(l -> new MapSqlParameterSource()
                .addValue("campagneId", id)
                .addValue("execution", execution)
                .addValue("clientId", l.message().getClientId())
                .addValue("canal", l.message().getCanal())
                .addValue("contactId", l.contactId())
                .addValue("numeroDestinataire", l.message().getNumeroDestinataire())
                .addValue("emailDestinataire", l.message().getEmailDestinataire())
                .addValue("numeroExpediteur", l.message().getNumeroExpediteur())
                .addValue("emailReponse", l.message().getEmailReponse())
                .addValue("objet", l.message().getObjet())
                .addValue("contenu", l.message().getContenu())
                .addValue("statut", l.message().getStatut())
                .addValue("motif", l.message().getMotif())).toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("""
                insert into crm_campagne_message (campagne_id, execution, facture_id, client_id, canal, contact_id, numero_destinataire, email_destinataire,
                                                  numero_expediteur, email_reponse, objet, contenu, statut, motif, created_at)
                values (:campagneId, :execution, null, :clientId, :canal, :contactId, :numeroDestinataire, :emailDestinataire,
                        :numeroExpediteur, :emailReponse, :objet, :contenu, :statut, :motif, utc_timestamp())
                """, lots);
    }

    // --- Generation des messages ---------------------------------------------------------------

    private List<MessageCampagneDTO> tousLesMessages(Campagne campagne) {
        if (NON_VALIDEES.contains(campagne.dto().getStatut())) {
            return generer(campagne).stream().map(Ligne::message).toList();
        }
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT_MESSAGES, Tuple.class).setParameter("id", campagne.dto().getId()).getResultList();
        return lignes.stream().map(this::mapTupleToMessage).toList();
    }

    /**
     * Un message par numero retenu par la liste (WhatsApp, SMS) ou un e-mail par client. Un numero partage par deux
     * clients ne recoit qu'un message ; un client desabonne ou sans coordonnees est exclu avec son motif.
     */
    private List<Ligne> generer(Campagne campagne) {
        CampagneMarketingDTO dto = campagne.dto();
        List<Long> clients = listeDiffusionService.clients(dto.getListeId(), campagne.perimetre());
        if (clients.isEmpty()) {
            return List.of();
        }
        ListeDiffusionDTO liste = listeDiffusionService.consulterDefinition(dto.getListeId());
        String canal = dto.getCanal();
        Map<String, String> sourcesParCode = variableMessageService.sourcesParCode();
        Set<String> utilisees = new LinkedHashSet<>(GabaritMessage.variables(campagne.contenu()));
        if (campagne.objet() != null) {
            utilisees.addAll(GabaritMessage.variables(campagne.objet()));
        }
        Map<String, String> aliasParSource = new LinkedHashMap<>();
        StringBuilder colonnes = new StringBuilder();
        for (String code : utilisees) {
            String source = sourcesParCode.get(code);
            if (source == null || !source.startsWith("client.") || aliasParSource.containsKey(source)) {
                continue;
            }
            String colonne = source.substring("client.".length());
            if (COLONNE.matcher(colonne).matches()) {
                String alias = "v" + aliasParSource.size();
                aliasParSource.put(source, alias);
                colonnes.append(", c.`").append(colonne).append("` as ").append(alias);
            }
        }
        @SuppressWarnings("unchecked")
        List<Tuple> fiches = em.createNativeQuery("""
                select c.id as clientId, c.code as clientCode, c.raison_sociale as clientRaisonSociale, c.agence_id as agenceId,
                       c.email as clientEmail, c.desabonne_marketing_le as desabonneLe, ag.libelle as agence, sg.libelle as segment
                """ + colonnes + """

                from crm_client c
                join crm_agence ag on ag.id = c.agence_id
                left join crm_segment sg on sg.code = c.segmentation
                where c.id in (:ids)
                order by c.raison_sociale, c.code
                """, Tuple.class).setParameter("ids", clients).getResultList();
        Map<Long, List<Object[]>> contacts = new HashMap<>();
        if (!"EMAIL".equals(canal)) {
            @SuppressWarnings("unchecked")
            List<Object[]> lignes = em.createNativeQuery("""
                    select client_id, id, numero_whatsapp from crm_contact
                    where client_id in (:ids) and statut = 'ACTIF' and fonction in (:fonctions) and numero_whatsapp is not null
                    order by client_id, fonction = 'PRINCIPAL' desc, rang, id
                    """).setParameter("ids", clients).setParameter("fonctions", liste.getFonctions()).getResultList();
            lignes.forEach(l -> contacts.computeIfAbsent(((Number) l[0]).longValue(), k -> new ArrayList<>()).add(l));
        }

        Map<String, String> valeursPromotions = dto.getPromotionIds().isEmpty() ? Map.of() : promotionService.valeursCampagne(dto.getPromotionIds());

        List<Ligne> resultat = new ArrayList<>();
        Set<String> servis = new HashSet<>();
        for (Tuple t : fiches) {
            Map<String, String> valeurs = new HashMap<>();
            sourcesParCode.forEach((code, source) -> valeurs.put(code, ModeleMessageServiceImpl.sourcePromotion(source)
                    ? valeursPromotions.getOrDefault(source, "") : valeur(t, source, aliasParSource, dto)));
            Long clientId = t.get("clientId", Long.class);
            if (t.get("desabonneLe") != null) {
                resultat.add(ligne(t, dto, null, null, null, "Client désabonné (STOP)", campagne, valeurs));
            } else if ("EMAIL".equals(canal)) {
                String adresse = SelectionCampagne.premiereAdresse(t.get("clientEmail", String.class));
                String motif = adresse == null ? (t.get("clientEmail") == null || t.get("clientEmail", String.class).isBlank()
                        ? "Client sans adresse e-mail" : "Adresse e-mail du client invalide")
                        : !servis.add(adresse) ? "Adresse déjà servie dans la campagne" : null;
                resultat.add(ligne(t, dto, null, null, adresse, motif, campagne, valeurs));
            } else {
                List<Object[]> numeros = contacts.getOrDefault(clientId, List.of());
                if (numeros.isEmpty()) {
                    resultat.add(ligne(t, dto, null, null, null,
                            "SMS".equals(canal) ? "Client sans numéro de téléphone" : "Client sans numéro WhatsApp", campagne, valeurs));
                }
                for (Object[] n : numeros) {
                    String numero = (String) n[2];
                    resultat.add(ligne(t, dto, ((Number) n[1]).longValue(), numero, null,
                            servis.add(numero) ? null : "Numéro déjà servi dans la campagne", campagne, valeurs));
                }
            }
        }
        return resultat;
    }

    private Ligne ligne(Tuple t, CampagneMarketingDTO dto, Long contactId, String numero, String adresse, String motif, Campagne campagne,
                        Map<String, String> valeurs) {
        boolean email = "EMAIL".equals(dto.getCanal());
        MessageCampagneDTO message = MessageCampagneDTO.builder()
                .clientId(t.get("clientId", Long.class))
                .clientCode(t.get("clientCode", String.class))
                .clientRaisonSociale(t.get("clientRaisonSociale", String.class))
                .agenceId(t.get("agenceId", Long.class))
                .canal(dto.getCanal())
                .numeroDestinataire(numero)
                .emailDestinataire(adresse)
                .agentNom(dto.getCreePar())
                .numeroExpediteur("WHATSAPP".equals(dto.getCanal()) ? dto.getNumeroExpediteur() : null)
                .emailReponse(email ? campagne.emailAuteur() : null)
                .objet(motif == null && email ? GabaritMessage.rendre(campagne.objet(), valeurs) : null)
                .contenu(motif == null ? GabaritMessage.rendre(campagne.contenu(), valeurs) : null)
                .statut(motif == null ? A_ENVOYER : EXCLU)
                .motif(motif)
                .build();
        return new Ligne(message, motif == null ? contactId : null);
    }

    private String valeur(Tuple t, String source, Map<String, String> aliasParSource, CampagneMarketingDTO dto) {
        return switch (source) {
            case "calcul.agence" -> t.get("agence", String.class);
            case "calcul.segment" -> t.get("segment", String.class);
            case "calcul.expediteur_nom" -> dto.getCreePar();
            case "calcul.expediteur_numero" -> SelectionCampagne.numeroLisible(dto.getNumeroExpediteur());
            default -> {
                String alias = aliasParSource.get(source);
                yield alias == null ? "" : SelectionCampagne.formater(t.get(alias));
            }
        };
    }

    private void enregistrerComptes(Long id, List<Ligne> lignes) {
        List<MessageCampagneDTO> envoyables = lignes.stream().filter(Ligne::envoyable).map(Ligne::message).toList();
        em.createNativeQuery("""
                update crm_campagne set nb_clients = :nbClients, nb_messages = :nbMessages, nb_exclus = :nbExclus, agences_cibles = :agences
                where id = :id
                """)
                .setParameter("nbClients", envoyables.stream().map(MessageCampagneDTO::getClientId).distinct().count())
                .setParameter("nbMessages", envoyables.size())
                .setParameter("nbExclus", lignes.size() - envoyables.size())
                .setParameter("agences", joindre(envoyables.stream().map(MessageCampagneDTO::getAgenceId).distinct().sorted().toList()))
                .setParameter("id", id)
                .executeUpdate();
    }

    // --- Controles -----------------------------------------------------------------------------

    private void verifierDemande(CampagneMarketingDemandeDTO demande) {
        @SuppressWarnings("unchecked")
        List<String> modeles = em.createNativeQuery("select statut from crm_modele_message where id = :id and module = 'MARKETING'")
                .setParameter("id", demande.getModeleId()).getResultList();
        if (modeles.isEmpty() || !"ACTIF".equals(modeles.getFirst())) {
            throw new ErreurMetierException("Choisissez un modèle promotionnel actif.");
        }
        @SuppressWarnings("unchecked")
        List<String> listes = em.createNativeQuery("select statut from crm_liste_diffusion where id = :id")
                .setParameter("id", demande.getListeId()).getResultList();
        if (listes.isEmpty() || !"ACTIF".equals(listes.getFirst())) {
            throw new ErreurMetierException("Choisissez une liste de diffusion active.");
        }
        if (demande.getDateExecution() != null && demande.getDateExecution().isBefore(maintenant().minusMinutes(1))) {
            throw new ErreurMetierException("La date d'exécution est déjà passée.");
        }
        promotionService.verifierCitables(promotions(demande));
        @SuppressWarnings("unchecked")
        List<String> contenus = em.createNativeQuery("select concat(coalesce(objet, ''), ' ', contenu) from crm_modele_message where id = :id")
                .setParameter("id", demande.getModeleId()).getResultList();
        verifierPromotionsDuModele(contenus.getFirst(), promotions(demande));
        Recurrence.verifier(demande.getRecurrence(), demande.getFinRecurrence(), demande.getDateExecution(), maintenant().toLocalDate());
        verifierPiecesJointes(demande);
    }

    /** L'affiche doit avoir ete deposee ; la liste des produits demande une promotion ; un SMS ne transporte aucun fichier. */
    private void verifierPiecesJointes(CampagneMarketingDemandeDTO demande) {
        if (demande.getAfficheId() == null && !demande.isJoindreProduits()) {
            return;
        }
        String canal = (String) em.createNativeQuery("select canal from crm_modele_message where id = :id")
                .setParameter("id", demande.getModeleId()).getSingleResult();
        if ("SMS".equals(canal)) {
            throw new ErreurMetierException("Un SMS ne transporte pas de pièce jointe : retirez l'affiche et la liste des produits.");
        }
        if (demande.getAfficheId() != null && !"AFFICHE".equals(pieceJointeService.decrire(demande.getAfficheId()).getNature())) {
            throw new ErreurMetierException("Déposez l'affiche avant de l'associer à la campagne.");
        }
        if (demande.isJoindreProduits() && promotions(demande).isEmpty()) {
            throw new ErreurMetierException("Cochez au moins une promotion pour joindre la liste de ses produits.");
        }
    }

    private static List<Long> promotions(CampagneMarketingDemandeDTO demande) {
        return demande.getPromotionIds() == null ? List.of() : demande.getPromotionIds().stream().filter(Objects::nonNull).distinct().toList();
    }

    /** Un modele qui cite {produits_promotion} ou {promotion_nom} demande au moins une promotion. */
    private void verifierPromotionsDuModele(String texte, List<Long> promotions) {
        if (!promotions.isEmpty()) {
            return;
        }
        Map<String, String> sources = variableMessageService.sourcesParCode();
        List<String> citees = GabaritMessage.variables(texte).stream().filter(v -> ModeleMessageServiceImpl.sourcePromotion(sources.get(v)))
                .map(v -> "{" + v + "}").toList();
        if (!citees.isEmpty()) {
            throw new ErreurMetierException("Le modèle cite " + String.join(", ", citees) + " : choisissez au moins une promotion.");
        }
    }

    /** Les promotions citees doivent etre encore en cours a la soumission et a la validation. */
    private void verifierPromotions(Campagne campagne) {
        promotionService.verifierCitables(campagne.dto().getPromotionIds());
        verifierPromotionsDuModele(Objects.requireNonNullElse(campagne.objet(), "") + " " + campagne.contenu(), campagne.dto().getPromotionIds());
    }

    /** Le WhatsApp part du numero de l'auteur : sans numero, ou auteur desactive, rien ne peut partir. */
    private void verifierExpediteur(Campagne campagne) {
        if (!campagne.auteurActif()) {
            throw new ErreurMetierException("Le compte de " + campagne.dto().getCreePar() + ", auteur de la campagne, est désactivé.");
        }
        if ("WHATSAPP".equals(campagne.dto().getCanal()) && campagne.dto().getNumeroExpediteur() == null) {
            throw new ErreurMetierException("Le compte de " + campagne.dto().getCreePar()
                    + " n'a pas de numéro WhatsApp : l'administrateur l'enregistre dans Utilisateurs, puis il se relie par QR code.");
        }
    }

    /** Un valideur limite a ses agences ne valide pas une campagne qui en sort. */
    private void verifierAgencesDuValideur(List<Ligne> lignes) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (perimetre.isVisionGlobale()) {
            return;
        }
        boolean horsPerimetre = lignes.stream().filter(Ligne::envoyable)
                .anyMatch(l -> !perimetre.getAgenceIds().contains(l.message().getAgenceId()));
        if (horsPerimetre) {
            throw new ErreurMetierException("La campagne vise des clients d'autres agences que les vôtres : elle se valide par la direction.");
        }
    }

    private void verifierAuteurOuValideur(CampagneMarketingDTO campagne) {
        if (!campagne.getCreeParId().equals(utilisateurCourantService.utilisateur().getId()) && !estValideur()) {
            throw new ErreurMetierException("Seuls son auteur et les personnes qui valident les campagnes peuvent intervenir sur celle-ci.");
        }
    }

    private boolean estValideur() {
        return utilisateurCourantService.utilisateur().getAuthorities().stream().anyMatch(a -> VALIDEURS.equals(a.getAuthority()));
    }

    // --- Lecture -------------------------------------------------------------------------------

    /** Hors vision globale : ses campagnes et celles qui visent ses agences ; et seulement les messages de ses agences. */
    private void restreindre(StringBuilder where, Map<String, Object> parametres) {
        where.append(" and cp.module = 'MARKETING'");
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (perimetre.isVisionGlobale()) {
            return;
        }
        parametres.put("moi", perimetre.getUtilisateurId());
        StringBuilder agences = new StringBuilder();
        for (int i = 0; i < perimetre.getAgenceIds().size(); i++) {
            agences.append(" or find_in_set(:agence").append(i).append(", cp.agences_cibles) > 0");
            parametres.put("agence" + i, String.valueOf(perimetre.getAgenceIds().get(i)));
        }
        where.append(" and (cp.cree_par = :moi").append(agences).append(")");
    }

    private List<MessageCampagneDTO> visibles(List<MessageCampagneDTO> messages) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        return perimetre.isVisionGlobale() ? messages : messages.stream().filter(m -> perimetre.getAgenceIds().contains(m.getAgenceId())).toList();
    }

    /** Lecture par le planificateur, hors de toute session utilisateur. */
    private Campagne lireSansRestriction(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where cp.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Campagne introuvable");
        }
        return mapTupleToCampagne(lignes.getFirst());
    }

    private Campagne lire(Long id, boolean verrou) {
        StringBuilder where = new StringBuilder(" where cp.id = :id");
        Map<String, Object> parametres = new LinkedHashMap<>();
        parametres.put("id", id);
        restreindre(where, parametres);
        Query requete = em.createNativeQuery(SELECT + where + (verrou ? " for update" : ""), Tuple.class);
        parametres.forEach(requete::setParameter);
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Campagne introuvable");
        }
        return mapTupleToCampagne(lignes.getFirst());
    }

    private static LocalDateTime maintenant() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static boolean contient(String texte, String q) {
        return texte != null && texte.toLowerCase().contains(q);
    }

    private static String joindre(List<Long> ids) {
        return ids == null ? null : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static List<Long> separer(String ids) {
        if (ids == null) {
            return null;
        }
        return ids.isBlank() ? List.of() : Arrays.stream(ids.split(",")).map(Long::valueOf).toList();
    }

    Campagne mapTupleToCampagne(Tuple tuple) {
        CampagneMarketingDTO dto = CampagneMarketingDTO.builder()
                .id(tuple.get("id", Long.class))
                .nom(tuple.get("nom", String.class))
                .listeId(tuple.get("listeId", Long.class))
                .listeNom(tuple.get("listeNom", String.class))
                .modeleId(tuple.get("modeleId", Long.class))
                .modeleNom(tuple.get("modeleNom", String.class))
                .canal(tuple.get("canal", String.class))
                .promotionIds(Objects.requireNonNullElse(separer(tuple.get("promotionIds", String.class)), List.of()))
                .afficheId(tuple.get("afficheId", Long.class))
                .afficheNom(tuple.get("afficheNom", String.class))
                .afficheType(tuple.get("afficheType", String.class))
                .joindreProduits(Boolean.TRUE.equals(tuple.get("joindreProduits", Boolean.class)))
                .listeProduitsId(tuple.get("listeProduitsId", Long.class))
                .promotions(tuple.get("promotionsLibelles") == null ? List.of() : List.of(tuple.get("promotionsLibelles", String.class).split("\\|")))
                .dateExecution(tuple.get("dateExecution", LocalDateTime.class))
                .recurrence(tuple.get("recurrence", String.class))
                .finRecurrence(tuple.get("finRecurrence", java.time.LocalDate.class))
                .nbExecutions(((Number) tuple.get("nbExecutions")).intValue())
                .statut(tuple.get("statut", String.class))
                .nbClients(((Number) tuple.get("nbClients")).intValue())
                .nbMessages(((Number) tuple.get("nbMessages")).intValue())
                .nbExclus(((Number) tuple.get("nbExclus")).intValue())
                .nbEnvoyes(((Number) tuple.get("nbEnvoyes")).intValue())
                .nbEchecs(((Number) tuple.get("nbEchecs")).intValue())
                .creeParId(tuple.get("creeParId", Long.class))
                .creePar(tuple.get("creePar", String.class))
                .numeroExpediteur(tuple.get("numeroExpediteur", String.class))
                .agencesCibles(separer(tuple.get("agencesCibles", String.class)))
                .createdAt(tuple.get("createdAt", LocalDateTime.class))
                .soumiseLe(tuple.get("soumiseLe", LocalDateTime.class))
                .validePar(tuple.get("validePar", String.class))
                .valideLe(tuple.get("valideLe", LocalDateTime.class))
                .motifRejet(tuple.get("motifRejet", String.class))
                .rejetePar(tuple.get("rejetePar", String.class))
                .lanceLe(tuple.get("lanceLe", LocalDateTime.class))
                .termineLe(tuple.get("termineLe", LocalDateTime.class))
                .annulePar(tuple.get("annulePar", String.class))
                .annuleLe(tuple.get("annuleLe", LocalDateTime.class))
                .motifAnnulation(tuple.get("motifAnnulation", String.class))
                .build();
        return new Campagne(dto, tuple.get("modeleObjet", String.class), tuple.get("modeleContenu", String.class),
                tuple.get("modeleStatut", String.class), tuple.get("emailAuteur", String.class), "ACTIF".equals(tuple.get("statutAuteur", String.class)),
                separer(tuple.get("perimetreAgences", String.class)), tuple.get("debutRecurrence", LocalDateTime.class));
    }

    MessageCampagneDTO mapTupleToMessage(Tuple tuple) {
        return MessageCampagneDTO.builder()
                .id(tuple.get("id", Long.class))
                .clientId(tuple.get("clientId", Long.class))
                .clientCode(tuple.get("clientCode", String.class))
                .clientRaisonSociale(tuple.get("clientRaisonSociale", String.class))
                .agenceId(tuple.get("agenceId", Long.class))
                .canal(tuple.get("canal", String.class))
                .numeroDestinataire(tuple.get("numeroDestinataire", String.class))
                .emailDestinataire(tuple.get("emailDestinataire", String.class))
                .numeroExpediteur(tuple.get("numeroExpediteur", String.class))
                .emailReponse(tuple.get("emailReponse", String.class))
                .objet(tuple.get("objet", String.class))
                .contenu(tuple.get("contenu", String.class))
                .statut(tuple.get("statut", String.class))
                .motif(tuple.get("motif", String.class))
                .envoyeLe(tuple.get("envoyeLe", LocalDateTime.class))
                .distribueLe(tuple.get("distribueLe", LocalDateTime.class))
                .luLe(tuple.get("luLe", LocalDateTime.class))
                .build();
    }
}
