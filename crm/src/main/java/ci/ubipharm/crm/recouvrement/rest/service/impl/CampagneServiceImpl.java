package ci.ubipharm.crm.recouvrement.rest.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.recouvrement.rest.service.CampagneService;
import ci.ubipharm.crm.recouvrement.rest.service.PortefeuilleService;
import ci.ubipharm.crm.recouvrement.rest.service.dto.AgentCampagneDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.CampagneCriteresDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.CampagneDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.CampagneDemandeDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.CriteresCampagneDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.MessageCampagneCriteresDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.MessageCampagneDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.MotifDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.MotifExclusionDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.RecapitulatifCampagneDTO;
import ci.ubipharm.crm.recouvrement.rest.service.impl.SelectionCampagne.Ligne;
import ci.ubipharm.crm.recouvrement.rest.service.impl.SelectionCampagne.Perimetre;
import ci.ubipharm.crm.securite.rest.service.JournalActionService;
import ci.ubipharm.crm.securite.rest.service.PerimetreService;
import ci.ubipharm.crm.securite.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.securite.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class CampagneServiceImpl implements CampagneService {

    /** Profils qui valident les campagnes et peuvent intervenir sur celles des autres. */
    private static final Set<String> GESTIONNAIRES = Set.of("DROIT_CAMPAGNES_VALIDER");
    private static final Set<String> NON_VALIDEES = Set.of("BROUILLON", "A_VALIDER");
    private static final int EXEMPLES = 3;

    private static final String SELECT = """
            select cp.id as id, cp.nom as nom, cp.type_campagne as typeCampagne, cp.modele_id as modeleId, mo.nom as modeleNom,
                   mo.canal as canal, mo.objet as modeleObjet, mo.contenu as modeleContenu, mo.statut as modeleStatut, cp.criteres as criteres, cp.date_execution as dateExecution,
                   cp.statut as statut, cp.perimetre_agences as perimetreAgences, cp.perimetre_agents as perimetreAgents,
                   cp.nb_clients as nbClients, cp.nb_factures as nbFactures, cp.nb_messages as nbMessages, cp.nb_exclus as nbExclus,
                   cp.nb_envoyes as nbEnvoyes, cp.nb_echecs as nbEchecs, cp.cree_par as creeParId, uc.nom_complet as creePar,
                   cp.created_at as createdAt, cp.soumise_le as soumiseLe, uv.nom_complet as validePar, cp.valide_le as valideLe,
                   cp.motif_rejet as motifRejet, ur.nom_complet as rejetePar, cp.lance_le as lanceLe, cp.termine_le as termineLe,
                   ua.nom_complet as annulePar, cp.annule_le as annuleLe, cp.motif_annulation as motifAnnulation
            from crm_campagne cp
            join crm_modele_message mo on mo.id = cp.modele_id
            join crm_utilisateur uc on uc.id = cp.cree_par
            left join crm_utilisateur uv on uv.id = cp.valide_par
            left join crm_utilisateur ur on ur.id = cp.rejete_par
            left join crm_utilisateur ua on ua.id = cp.annule_par
            """;

    private static final String SELECT_MESSAGES = """
            select m.id as id, m.facture_id as factureId, f.reference as factureReference, f.date_echeance as dateEcheance,
                   f.solde as solde, m.client_id as clientId, c.code as clientCode, c.raison_sociale as clientRaisonSociale,
                   c.agence_id as agenceId, m.canal as canal, m.numero_destinataire as numeroDestinataire, m.email_destinataire as emailDestinataire,
                   m.agent_id as agentId, u.nom_complet as agentNom, m.numero_expediteur as numeroExpediteur, m.email_reponse as emailReponse,
                   m.objet as objet, m.contenu as contenu, m.statut as statut, m.motif as motif,
                   m.envoye_le as envoyeLe, m.distribue_le as distribueLe, m.lu_le as luLe
            from crm_campagne_message m
            join crm_facture f on f.id = m.facture_id
            join crm_client c on c.id = m.client_id
            left join crm_utilisateur u on u.id = m.agent_id
            where m.campagne_id = :id
            order by u.nom_complet, c.code, f.date_echeance, f.reference
            """;

    private final EntityManager em;
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper jsonMapper;
    private final SelectionCampagne selectionCampagne;
    private final PerimetreService perimetreService;
    private final PortefeuilleService portefeuilleService;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;

    /** Campagne lue en base, avec ce qu'il faut pour regenerer ses messages. */
    private record Campagne(CampagneDTO dto, SelectionCampagne.Modele modele, String modeleStatut, Perimetre perimetre) {
    }

    @Override
    @Transactional(readOnly = true)
    public PageDTO<CampagneDTO> rechercher(CampagneCriteresDTO criteres) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> parametres = new LinkedHashMap<>();
        restreindre(where, parametres);
        if (criteres.getStatut() != null) {
            where.append(" and cp.statut = :statut");
            parametres.put("statut", criteres.getStatut());
        }
        if (criteres.getTypeCampagne() != null) {
            where.append(" and cp.type_campagne = :typeCampagne");
            parametres.put("typeCampagne", criteres.getTypeCampagne());
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
        return PageDTO.<CampagneDTO>builder()
                .elements(lignes.stream().map(t -> mapTupleToCampagne(t).dto()).toList())
                .total(total)
                .page(criteres.getPage())
                .taille(criteres.getTaille())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CampagneDTO consulter(Long id) {
        return lire(id, false).dto();
    }

    @Override
    @Transactional
    public CampagneDTO creer(CampagneDemandeDTO demande) {
        verifierDemande(demande);
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        List<Long> agentsGeres = portefeuilleService.agentsGeres();
        em.createNativeQuery("""
                insert into crm_campagne (nom, type_campagne, modele_id, criteres, date_execution, perimetre_agences, perimetre_agents,
                                          cree_par, created_at)
                values (:nom, :typeCampagne, :modeleId, :criteres, :dateExecution, :perimetreAgences, :perimetreAgents, :creePar, utc_timestamp())
                """)
                .setParameter("nom", demande.getNom().trim())
                .setParameter("typeCampagne", demande.getTypeCampagne())
                .setParameter("modeleId", demande.getModeleId())
                .setParameter("criteres", jsonMapper.writeValueAsString(criteres(demande)))
                .setParameter("dateExecution", demande.getDateExecution())
                .setParameter("perimetreAgences", agentsGeres == null && !perimetre.isVisionGlobale() ? joindre(perimetre.getAgenceIds()) : null)
                .setParameter("perimetreAgents", joindre(agentsGeres))
                .setParameter("creePar", perimetre.getUtilisateurId())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        journalActionService.enregistrer("CAMPAGNE_CREATION", "crm_campagne", id, demande.getNom().trim());
        return consulter(id);
    }

    @Override
    @Transactional
    public CampagneDTO modifier(Long id, CampagneDemandeDTO demande) {
        CampagneDTO campagne = lire(id, true).dto();
        verifierAuteurOuGestionnaire(campagne);
        if (!NON_VALIDEES.contains(campagne.getStatut())) {
            throw new ErreurMetierException("Une campagne validée ne se modifie plus ; annulez-la et créez-en une autre.");
        }
        verifierDemande(demande);
        em.createNativeQuery("""
                update crm_campagne set nom = :nom, type_campagne = :typeCampagne, modele_id = :modeleId, criteres = :criteres,
                       date_execution = :dateExecution, statut = 'BROUILLON', soumise_le = null, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("nom", demande.getNom().trim())
                .setParameter("typeCampagne", demande.getTypeCampagne())
                .setParameter("modeleId", demande.getModeleId())
                .setParameter("criteres", jsonMapper.writeValueAsString(criteres(demande)))
                .setParameter("dateExecution", demande.getDateExecution())
                .setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("CAMPAGNE_MODIFICATION", "crm_campagne", id, demande.getNom().trim());
        return consulter(id);
    }

    @Override
    @Transactional(readOnly = true)
    public RecapitulatifCampagneDTO recapitulatif(Long id) {
        List<MessageCampagneDTO> messages = visibles(tousLesMessages(lire(id, false)));
        List<MessageCampagneDTO> envoyables = messages.stream().filter(m -> !SelectionCampagne.EXCLU.equals(m.getStatut())
                && !"ANNULE".equals(m.getStatut())).toList();

        Map<Long, List<MessageCampagneDTO>> parAgent = envoyables.stream()
                .collect(Collectors.groupingBy(MessageCampagneDTO::getAgentId, LinkedHashMap::new, Collectors.toList()));
        List<AgentCampagneDTO> agents = parAgent.values().stream()
                .map(liste -> AgentCampagneDTO.builder()
                        .agentId(liste.getFirst().getAgentId())
                        .agentNom(liste.getFirst().getAgentNom())
                        .numeroExpediteur(liste.getFirst().getNumeroExpediteur())
                        .nbClients(liste.stream().map(MessageCampagneDTO::getClientId).distinct().count())
                        .nbMessages(liste.size())
                        .build())
                .toList();
        List<MotifExclusionDTO> exclusions = messages.stream().filter(m -> SelectionCampagne.EXCLU.equals(m.getStatut()))
                .collect(Collectors.groupingBy(MessageCampagneDTO::getMotif, Collectors.counting()))
                .entrySet().stream()
                .map(e -> MotifExclusionDTO.builder().motif(e.getKey()).nb(e.getValue()).build())
                .sorted(Comparator.comparingLong(MotifExclusionDTO::getNb).reversed().thenComparing(MotifExclusionDTO::getMotif))
                .toList();
        return RecapitulatifCampagneDTO.builder()
                .nbClients(envoyables.stream().map(MessageCampagneDTO::getClientId).distinct().count())
                .nbFactures(envoyables.size())
                .nbMessages(envoyables.size())
                .nbExclus(messages.stream().filter(m -> SelectionCampagne.EXCLU.equals(m.getStatut())).count())
                .totalSolde(envoyables.stream().map(MessageCampagneDTO::getSolde).reduce(BigDecimal.ZERO, BigDecimal::add))
                .agents(agents)
                .exclusions(exclusions)
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
        if (criteres.getAgentId() != null) {
            filtre = filtre.and(m -> criteres.getAgentId().equals(m.getAgentId()));
        }
        if (criteres.getQ() != null && !criteres.getQ().isBlank()) {
            String q = criteres.getQ().trim().toLowerCase();
            filtre = filtre.and(m -> contient(m.getFactureReference(), q) || contient(m.getClientCode(), q) || contient(m.getClientRaisonSociale(), q));
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
    public CampagneDTO soumettre(Long id) {
        Campagne campagne = lire(id, true);
        verifierAuteurOuGestionnaire(campagne.dto());
        if (!"BROUILLON".equals(campagne.dto().getStatut())) {
            throw new ErreurMetierException("Seul un brouillon peut être soumis à validation.");
        }
        List<Ligne> lignes = generer(campagne);
        long envoyables = lignes.stream().filter(Ligne::envoyable).count();
        if (envoyables == 0) {
            throw new ErreurMetierException("La campagne ne contient aucun message à envoyer.");
        }
        em.createNativeQuery("update crm_campagne set statut = 'A_VALIDER', soumise_le = utc_timestamp(), motif_rejet = null, rejete_par = null, "
                + "updated_at = utc_timestamp() where id = :id").setParameter("id", id).executeUpdate();
        enregistrerComptes(id, lignes);
        journalActionService.enregistrer("CAMPAGNE_SOUMISSION", "crm_campagne", id, envoyables + " message(s)");
        return consulter(id);
    }

    @Override
    @Transactional
    public CampagneDTO valider(Long id) {
        Campagne campagne = lire(id, true);
        if (!"A_VALIDER".equals(campagne.dto().getStatut())) {
            throw new ErreurMetierException("Seule une campagne soumise peut être validée.");
        }
        if (!"ACTIF".equals(campagne.modeleStatut())) {
            throw new ErreurMetierException("Le modèle « " + campagne.dto().getModeleNom() + " » a été archivé ; choisissez-en un autre.");
        }
        List<Ligne> lignes = generer(campagne);
        if (lignes.stream().noneMatch(Ligne::envoyable)) {
            throw new ErreurMetierException("La campagne ne contient plus aucun message à envoyer.");
        }
        SqlParameterSource[] lots = lignes.stream().map(l -> new MapSqlParameterSource()
                .addValue("campagneId", id)
                .addValue("factureId", l.message().getFactureId())
                .addValue("clientId", l.message().getClientId())
                .addValue("contactId", l.contactId())
                .addValue("canal", campagne.modele().canal())
                .addValue("numeroDestinataire", l.message().getNumeroDestinataire())
                .addValue("emailDestinataire", l.message().getEmailDestinataire())
                .addValue("agentId", l.message().getAgentId())
                .addValue("numeroExpediteur", l.message().getNumeroExpediteur())
                .addValue("emailReponse", l.message().getEmailReponse())
                .addValue("objet", l.message().getObjet())
                .addValue("contenu", l.message().getContenu())
                .addValue("statut", l.message().getStatut())
                .addValue("motif", l.message().getMotif())).toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("""
                insert into crm_campagne_message (campagne_id, facture_id, client_id, canal, contact_id, numero_destinataire, email_destinataire,
                                                  agent_id, numero_expediteur, email_reponse, objet, contenu, statut, motif, created_at)
                values (:campagneId, :factureId, :clientId, :canal, :contactId, :numeroDestinataire, :emailDestinataire, :agentId,
                        :numeroExpediteur, :emailReponse, :objet, :contenu, :statut, :motif, utc_timestamp())
                """, lots);
        em.createNativeQuery("update crm_campagne set statut = 'VALIDEE', valide_par = :validePar, valide_le = utc_timestamp(), "
                + "updated_at = utc_timestamp() where id = :id")
                .setParameter("validePar", utilisateurCourantService.utilisateur().getId())
                .setParameter("id", id)
                .executeUpdate();
        enregistrerComptes(id, lignes);
        em.createNativeQuery("""
                update crm_facture f join crm_campagne_message m on m.facture_id = f.id
                set f.statut = 'RELANCE_PROGRAMMEE', f.updated_at = utc_timestamp()
                where m.campagne_id = :id and m.statut = 'A_ENVOYER' and f.statut = 'A_RELANCER'
                """).setParameter("id", id).executeUpdate();
        journalActionService.enregistrer("CAMPAGNE_VALIDATION", "crm_campagne", id,
                lignes.stream().filter(Ligne::envoyable).count() + " message(s)");

        LocalDateTime execution = campagne.dto().getDateExecution();
        if (execution == null || !execution.isAfter(maintenant())) {
            demarrer(id, false);
        }
        return consulter(id);
    }

    @Override
    @Transactional
    public CampagneDTO rejeter(Long id, MotifDTO motif) {
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
        journalActionService.enregistrer("CAMPAGNE_REJET", "crm_campagne", id, motif.getMotif().trim());
        return consulter(id);
    }

    @Override
    @Transactional
    public CampagneDTO lancer(Long id) {
        if (!"VALIDEE".equals(lire(id, true).dto().getStatut())) {
            throw new ErreurMetierException("Seule une campagne validée et pas encore démarrée peut être lancée.");
        }
        em.createNativeQuery("update crm_campagne set date_execution = utc_timestamp() where id = :id").setParameter("id", id).executeUpdate();
        demarrer(id, false);
        return consulter(id);
    }

    @Override
    @Transactional
    public CampagneDTO annuler(Long id, MotifDTO motif) {
        CampagneDTO campagne = lire(id, true).dto();
        verifierAuteurOuGestionnaire(campagne);
        if ("TERMINEE".equals(campagne.getStatut()) || "ANNULEE".equals(campagne.getStatut())) {
            throw new ErreurMetierException("La campagne est déjà " + ("TERMINEE".equals(campagne.getStatut()) ? "terminée." : "annulée."));
        }
        if (!NON_VALIDEES.contains(campagne.getStatut()) && !estGestionnaire()) {
            throw new ErreurMetierException("Une campagne validée ne peut être annulée que par la Direction du Recouvrement.");
        }
        @SuppressWarnings("unchecked")
        List<Long> factures = em.createNativeQuery("select facture_id from crm_campagne_message where campagne_id = :id and statut = 'A_ENVOYER'")
                .setParameter("id", id).getResultList();
        em.createNativeQuery("update crm_campagne_message set statut = 'ANNULE', motif = 'Campagne annulee', updated_at = utc_timestamp() "
                + "where campagne_id = :id and statut = 'A_ENVOYER'").setParameter("id", id).executeUpdate();
        if (!factures.isEmpty()) {
            // La facture redevient a relancer si aucune autre campagne ne doit l'envoyer
            em.createNativeQuery("""
                    update crm_facture f set f.statut = 'A_RELANCER', f.updated_at = utc_timestamp()
                    where f.id in (:factures) and f.statut = 'RELANCE_PROGRAMMEE'
                      and not exists (select 1 from crm_campagne_message m where m.facture_id = f.id and m.statut = 'A_ENVOYER')
                    """).setParameter("factures", factures).executeUpdate();
        }
        em.createNativeQuery("""
                update crm_campagne set statut = 'ANNULEE', annule_par = :annulePar, annule_le = utc_timestamp(), motif_annulation = :motif,
                       updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("annulePar", utilisateurCourantService.utilisateur().getId())
                .setParameter("motif", motif.getMotif().trim())
                .setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("CAMPAGNE_ANNULATION", "crm_campagne", id, motif.getMotif().trim());
        return consulter(id);
    }

    @Override
    @Transactional
    public int demarrerCampagnesEchues() {
        @SuppressWarnings("unchecked")
        List<Long> ids = em.createNativeQuery("select id from crm_campagne where statut = 'VALIDEE' and date_execution <= utc_timestamp() "
                + "order by date_execution for update").getResultList();
        ids.forEach(id -> demarrer(id, true));
        return ids.size();
    }

    /**
     * Passe la campagne en cours. Une facture soldee depuis la validation n'est plus relancee. L'envoi effectif
     * des messages A_ENVOYER est assure par le service WhatsApp (numero de chaque agent) ou par l'envoi d'e-mails.
     */
    private void demarrer(Long id, boolean systeme) {
        int annules = em.createNativeQuery("""
                update crm_campagne_message m join crm_facture f on f.id = m.facture_id
                set m.statut = 'ANNULE', m.motif = 'Facture soldee avant l''envoi', m.updated_at = utc_timestamp()
                where m.campagne_id = :id and m.statut = 'A_ENVOYER' and (f.solde <= 0 or f.statut = 'SOLDEE')
                """).setParameter("id", id).executeUpdate();
        long restants = ((Number) em.createNativeQuery("select count(*) from crm_campagne_message where campagne_id = :id and statut = 'A_ENVOYER'")
                .setParameter("id", id).getSingleResult()).longValue();
        em.createNativeQuery("""
                update crm_campagne set statut = :statut, lance_le = utc_timestamp(),
                       termine_le = case when :statut = 'TERMINEE' then utc_timestamp() end, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("statut", restants == 0 ? "TERMINEE" : "EN_COURS")
                .setParameter("id", id)
                .executeUpdate();
        String details = restants + " message(s) a envoyer" + (annules > 0 ? ", " + annules + " facture(s) soldee(s) retiree(s)" : "");
        if (systeme) {
            journalActionService.enregistrerSysteme("CAMPAGNE_LANCEMENT", "crm_campagne", id, details);
        } else {
            journalActionService.enregistrer("CAMPAGNE_LANCEMENT", "crm_campagne", id, details);
        }
    }

    /** Messages de la campagne : regeneres tant qu'elle n'est pas validee, lus en base ensuite. */
    private List<MessageCampagneDTO> tousLesMessages(Campagne campagne) {
        if (NON_VALIDEES.contains(campagne.dto().getStatut())) {
            return generer(campagne).stream().map(Ligne::message).toList();
        }
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT_MESSAGES, Tuple.class).setParameter("id", campagne.dto().getId()).getResultList();
        return lignes.stream().map(this::mapTupleToMessage).toList();
    }

    private List<Ligne> generer(Campagne campagne) {
        CampagneDTO dto = campagne.dto();
        LocalDate reference = dto.getDateExecution() == null || dto.getDateExecution().isBefore(maintenant())
                ? maintenant().toLocalDate() : dto.getDateExecution().toLocalDate();
        return selectionCampagne.selectionner(dto.getId(), dto.getTypeCampagne(), dto.getCriteres(), campagne.perimetre(), reference,
                campagne.modele());
    }

    private void enregistrerComptes(Long id, List<Ligne> lignes) {
        List<MessageCampagneDTO> envoyables = lignes.stream().filter(Ligne::envoyable).map(Ligne::message).toList();
        em.createNativeQuery("""
                update crm_campagne set nb_clients = :nbClients, nb_factures = :nbFactures, nb_messages = :nbMessages, nb_exclus = :nbExclus
                where id = :id
                """)
                .setParameter("nbClients", envoyables.stream().map(MessageCampagneDTO::getClientId).distinct().count())
                .setParameter("nbFactures", envoyables.size())
                .setParameter("nbMessages", envoyables.size())
                .setParameter("nbExclus", lignes.size() - envoyables.size())
                .setParameter("id", id)
                .executeUpdate();
    }

    /** Un agent ne voit que les messages de son portefeuille (et de ceux qu'il remplace), une direction d'agence ceux de ses agences. */
    private List<MessageCampagneDTO> visibles(List<MessageCampagneDTO> messages) {
        List<Long> agentsGeres = portefeuilleService.agentsGeres();
        if (agentsGeres != null) {
            return messages.stream().filter(m -> agentsGeres.contains(m.getAgentId())).toList();
        }
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (!perimetre.isVisionGlobale()) {
            return messages.stream().filter(m -> perimetre.getAgenceIds().contains(m.getAgenceId())).toList();
        }
        return messages;
    }

    /** Campagnes visibles : toutes en vision globale, sinon celles creees par l'utilisateur ou qui touchent son perimetre. */
    private void restreindre(StringBuilder where, Map<String, Object> parametres) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (perimetre.isVisionGlobale()) {
            return;
        }
        parametres.put("moi", perimetre.getUtilisateurId());
        List<Long> agentsGeres = portefeuilleService.agentsGeres();
        if (agentsGeres != null) {
            where.append(" and (cp.cree_par = :moi or exists (select 1 from crm_campagne_message m where m.campagne_id = cp.id"
                    + " and m.agent_id in (:agentsGeres)))");
            parametres.put("agentsGeres", agentsGeres);
        } else if (perimetre.getAgenceIds().isEmpty()) {
            where.append(" and cp.cree_par = :moi");
        } else {
            where.append(" and (cp.cree_par = :moi or exists (select 1 from crm_campagne_message m join crm_client c on c.id = m.client_id"
                    + " where m.campagne_id = cp.id and c.agence_id in (:agencesVisibles)))");
            parametres.put("agencesVisibles", perimetre.getAgenceIds());
        }
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

    private void verifierDemande(CampagneDemandeDTO demande) {
        if (!GabaritMessage.TYPES_CAMPAGNE.contains(demande.getTypeCampagne())) {
            throw new ErreurMetierException("Type de relance inconnu : " + demande.getTypeCampagne() + ".");
        }
        @SuppressWarnings("unchecked")
        List<Tuple> modeles = em.createNativeQuery("select nom as nom, type_campagne as typeCampagne, statut as statut from crm_modele_message where id = :id",
                Tuple.class).setParameter("id", demande.getModeleId()).getResultList();
        if (modeles.isEmpty() || !"ACTIF".equals(modeles.getFirst().get("statut", String.class))) {
            throw new ErreurMetierException("Choisissez un modèle de message actif.");
        }
        String typeModele = modeles.getFirst().get("typeCampagne", String.class);
        if (!"PERSONNALISEE".equals(demande.getTypeCampagne()) && !demande.getTypeCampagne().equals(typeModele)) {
            throw new ErreurMetierException("Le modèle « " + modeles.getFirst().get("nom", String.class) + " » est prévu pour un autre type de relance.");
        }
        if (demande.getDateExecution() != null && demande.getDateExecution().isBefore(maintenant().minusMinutes(1))) {
            throw new ErreurMetierException("La date d'exécution est déjà passée.");
        }
        selectionCampagne.verifier(demande.getTypeCampagne(), demande.getCriteres());
    }

    private void verifierAuteurOuGestionnaire(CampagneDTO campagne) {
        if (!campagne.getCreeParId().equals(utilisateurCourantService.utilisateur().getId()) && !estGestionnaire()) {
            throw new ErreurMetierException("Seuls son auteur et la Direction du Recouvrement peuvent intervenir sur cette campagne.");
        }
    }

    private boolean estGestionnaire() {
        return utilisateurCourantService.utilisateur().getAuthorities().stream().anyMatch(a -> GESTIONNAIRES.contains(a.getAuthority()));
    }

    private static CriteresCampagneDTO criteres(CampagneDemandeDTO demande) {
        return demande.getCriteres() == null ? new CriteresCampagneDTO() : demande.getCriteres();
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
        return ids.isBlank() ? List.of() : new ArrayList<>(Arrays.stream(ids.split(",")).map(Long::valueOf).toList());
    }

    Campagne mapTupleToCampagne(Tuple tuple) {
        CampagneDTO dto = CampagneDTO.builder()
                .id(tuple.get("id", Long.class))
                .nom(tuple.get("nom", String.class))
                .typeCampagne(tuple.get("typeCampagne", String.class))
                .modeleId(tuple.get("modeleId", Long.class))
                .modeleNom(tuple.get("modeleNom", String.class))
                .canal(tuple.get("canal", String.class))
                .criteres(jsonMapper.readValue(tuple.get("criteres", String.class), CriteresCampagneDTO.class))
                .dateExecution(tuple.get("dateExecution", LocalDateTime.class))
                .statut(tuple.get("statut", String.class))
                .nbClients(((Number) tuple.get("nbClients")).intValue())
                .nbFactures(((Number) tuple.get("nbFactures")).intValue())
                .nbMessages(((Number) tuple.get("nbMessages")).intValue())
                .nbExclus(((Number) tuple.get("nbExclus")).intValue())
                .nbEnvoyes(((Number) tuple.get("nbEnvoyes")).intValue())
                .nbEchecs(((Number) tuple.get("nbEchecs")).intValue())
                .creeParId(tuple.get("creeParId", Long.class))
                .creePar(tuple.get("creePar", String.class))
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
        SelectionCampagne.Modele modele = new SelectionCampagne.Modele(dto.getCanal(), tuple.get("modeleObjet", String.class),
                tuple.get("modeleContenu", String.class));
        return new Campagne(dto, modele, tuple.get("modeleStatut", String.class),
                new Perimetre(separer(tuple.get("perimetreAgences", String.class)), separer(tuple.get("perimetreAgents", String.class))));
    }

    MessageCampagneDTO mapTupleToMessage(Tuple tuple) {
        return MessageCampagneDTO.builder()
                .id(tuple.get("id", Long.class))
                .factureId(tuple.get("factureId", Long.class))
                .factureReference(tuple.get("factureReference", String.class))
                .dateEcheance(tuple.get("dateEcheance", LocalDate.class))
                .solde(tuple.get("solde", BigDecimal.class))
                .clientId(tuple.get("clientId", Long.class))
                .clientCode(tuple.get("clientCode", String.class))
                .clientRaisonSociale(tuple.get("clientRaisonSociale", String.class))
                .agenceId(tuple.get("agenceId", Long.class))
                .canal(tuple.get("canal", String.class))
                .numeroDestinataire(tuple.get("numeroDestinataire", String.class))
                .emailDestinataire(tuple.get("emailDestinataire", String.class))
                .agentId(tuple.get("agentId", Long.class))
                .agentNom(tuple.get("agentNom", String.class))
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
