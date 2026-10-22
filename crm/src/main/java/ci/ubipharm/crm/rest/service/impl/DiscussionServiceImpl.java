package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.DiscussionService;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.PasserelleMetaService;
import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.PieceJointeService;
import ci.ubipharm.crm.rest.service.VariableMessageService;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.ModeleMessageDTO;
import ci.ubipharm.crm.rest.service.dto.PieceJointeDTO;
import ci.ubipharm.crm.rest.service.PortefeuilleService;
import ci.ubipharm.crm.rest.service.dto.DiscussionCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.DiscussionDTO;
import ci.ubipharm.crm.rest.service.dto.MessageDiscussionDTO;
import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.rest.service.dto.NouvelleDiscussionDTO;
import ci.ubipharm.crm.rest.service.dto.ReponseDiscussionDTO;
import ci.ubipharm.crm.rest.service.dto.ResultatEnvoiDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DiscussionServiceImpl implements DiscussionService {

    private static final int FIL_MAX = 200;

    /** Une ligne par message echange, sortant ou entrant, sur WhatsApp. */
    private static final String ECHANGES = """
            select numero_expediteur as numero_agent, numero_destinataire as numero_client, coalesce(envoye_le, created_at) as le, 0 as entrant
            from crm_campagne_message where canal = 'WHATSAPP' and numero_destinataire is not null and statut in ('ENVOYE', 'DISTRIBUE', 'LU', 'ECHEC')
            union all
            select numero_expediteur, numero_destinataire, coalesce(envoye_le, created_at), 0
            from crm_message_sortant where statut in ('ENVOYE', 'DISTRIBUE', 'LU', 'ECHEC')
            union all
            select numero_agent, numero_client, recu_le, 1 from crm_message_recu
            """;

    /** Discussions, avec l'utilisateur du numero et le client du numero (son contact principal d'abord). */
    private static final String DISCUSSIONS = """
            from (select g.*, (select ct.client_id from crm_contact ct where ct.numero_whatsapp = g.numero_client and ct.statut = 'ACTIF'
                               order by ct.fonction = 'PRINCIPAL' desc, ct.rang, ct.id limit 1) as client_id
                  from (select e.numero_agent, e.numero_client, max(e.le) as derniere, sum(e.entrant) as nb_recus
                        from (%s) e group by e.numero_agent, e.numero_client) g) d
            left join crm_utilisateur u on u.numero_whatsapp = d.numero_agent
            left join crm_client c on c.id = d.client_id
            """.formatted(ECHANGES);

    /** A la meme seconde, une reponse ecrite ici suit le message du client, qui suit la relance. */
    private static final String FIL = """
            select concat('C', m.id) as cle, 'SORTANT' as sens, case cp.module when 'MARKETING' then 'PROMOTION' else 'RELANCE' end as origine,
                   cp.nom as source, 'TEXTE' as type, m.contenu as contenu, m.statut as statut, m.motif as motif, u.nom_complet as auteur,
                   coalesce(m.envoye_le, m.created_at) as le
            from crm_campagne_message m join crm_campagne cp on cp.id = m.campagne_id
            left join crm_utilisateur u on u.id = coalesce(m.agent_id, cp.cree_par)
            where m.canal = 'WHATSAPP' and m.numero_expediteur = :agent and m.numero_destinataire = :client and m.statut in ('ENVOYE', 'DISTRIBUE', 'LU', 'ECHEC')
            union all
            select concat('S', s.id), 'SORTANT', s.origine, e.nom, 'TEXTE', s.contenu, s.statut, s.motif, u.nom_complet, coalesce(s.envoye_le, s.created_at)
            from crm_message_sortant s left join crm_envoi_masse e on e.id = s.envoi_masse_id left join crm_utilisateur u on u.id = s.cree_par
            where s.numero_expediteur = :agent and s.numero_destinataire = :client and s.statut in ('ENVOYE', 'DISTRIBUE', 'LU', 'ECHEC')
            union all
            select concat('R', r.id), 'ENTRANT', 'REPONSE', null, r.type, r.contenu, null, null, r.nom_affiche, r.recu_le
            from crm_message_recu r where r.numero_agent = :agent and r.numero_client = :client
            order by le desc, case origine when 'DISCUSSION' then 2 when 'REPONSE' then 1 else 0 end desc, cle desc
            """;

    private final EntityManager em;
    private final PerimetreService perimetreService;
    private final PortefeuilleService portefeuilleService;
    private final ParametreService parametreService;
    private final PasserelleWhatsappService passerelle;
    private final PasserelleMetaService passerelleMeta;
    private final JournalActionService journalActionService;
    private final PieceJointeService pieceJointeService;
    private final VariableMessageService variableMessageService;

    @Override
    @Transactional(readOnly = true)
    public PageDTO<DiscussionDTO> rechercher(DiscussionCriteresDTO criteres) {
        Map<String, Object> parametres = new LinkedHashMap<>();
        StringBuilder where = new StringBuilder(perimetre(parametres));
        if (criteres.getAgentId() != null) {
            where.append(" and u.id = :agentId");
            parametres.put("agentId", criteres.getAgentId());
        }
        if (criteres.getClientId() != null) {
            where.append(" and d.client_id = :clientId");
            parametres.put("clientId", criteres.getClientId());
        }
        if (criteres.getQ() != null && !criteres.getQ().isBlank()) {
            String chiffres = criteres.getQ().replaceAll("\\D", "");
            where.append(" and (c.raison_sociale like :q or c.code = :code");
            parametres.put("q", "%" + criteres.getQ().trim() + "%");
            parametres.put("code", criteres.getQ().trim());
            if (chiffres.length() >= 6) {
                where.append(" or d.numero_client like :chiffres");
                parametres.put("chiffres", "%" + chiffres.substring(Math.max(0, chiffres.length() - 10)));
            }
            where.append(")");
        }
        Query comptage = em.createNativeQuery("select count(*) " + DISCUSSIONS + where);
        parametres.forEach(comptage::setParameter);
        long total = ((Number) comptage.getSingleResult()).longValue();
        Query requete = em.createNativeQuery("""
                select d.numero_agent as numeroAgent, u.nom_complet as agentNom, d.numero_client as numeroClient, d.client_id as clientId,
                       c.raison_sociale as clientNom, d.derniere as derniereActivite, d.nb_recus as nbRecus
                """ + DISCUSSIONS + where + " order by d.derniere desc, d.nb_recus desc, d.numero_client", Tuple.class);
        parametres.forEach(requete::setParameter);
        requete.setFirstResult(criteres.getPage() * criteres.getTaille());
        requete.setMaxResults(criteres.getTaille());
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        List<String> miens = numerosUtilisables();
        List<DiscussionDTO> discussions = lignes.stream().map(this::mapTupleToDto).toList();
        for (DiscussionDTO d : discussions) {
            d.setPeutRepondre(miens.contains(d.getNumeroAgent()));
            List<MessageDiscussionDTO> dernier = messages(d.getNumeroAgent(), d.getNumeroClient(), 1);
            if (!dernier.isEmpty()) {
                d.setDernierMessage(dernier.getFirst().getContenu());
                d.setDernierSens(dernier.getFirst().getSens());
            }
            if (d.getClientId() == null) {
                d.setNomAffiche(nomAffiche(d.getNumeroAgent(), d.getNumeroClient()));
            }
        }
        return PageDTO.<DiscussionDTO>builder().elements(discussions).total(total).page(criteres.getPage()).taille(criteres.getTaille()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageDiscussionDTO> fil(String numeroAgent, String numeroClient) {
        verifierAcces(numeroAgent, numeroClient);
        List<MessageDiscussionDTO> messages = new ArrayList<>(messages(numeroAgent, numeroClient, FIL_MAX));
        Collections.reverse(messages);
        return messages;
    }

    @Override
    @Transactional
    public MessageDiscussionDTO repondre(ReponseDiscussionDTO reponse) {
        if (!parametreService.oui("discussions.reponse_active", true)) {
            throw new ErreurMetierException("La réponse depuis le CRM est désactivée (Paramètres) : répondez depuis le téléphone.");
        }
        String agent = reponse.getNumeroAgent();
        String client = reponse.getNumeroClient();
        if (!numerosUtilisables().contains(agent)) {
            throw new ErreurMetierException("Vous ne pouvez répondre que depuis votre numéro ou celui d'un agent que vous remplacez.");
        }
        verifierAcces(agent, client);
        return envoyer(agent, client, reponse.getContenu().strip(), reponse.getPieceJointeIds());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> mesNumeros() {
        return numerosUtilisables();
    }

    @Override
    @Transactional
    public MessageDiscussionDTO demarrer(NouvelleDiscussionDTO demande) {
        if (!parametreService.oui("discussions.reponse_active", true)) {
            throw new ErreurMetierException("L'envoi depuis le CRM est désactivé (Paramètres) : écrivez depuis le téléphone.");
        }
        List<String> miens = numerosUtilisables();
        if (miens.isEmpty()) {
            throw new ErreurMetierException("Aucun numéro WhatsApp n'est relié à votre compte : demandez-le à l'administrateur (Utilisateurs).");
        }
        String agent = demande.getNumeroAgent() == null || demande.getNumeroAgent().isBlank() ? miens.getFirst() : demande.getNumeroAgent();
        if (!miens.contains(agent)) {
            throw new ErreurMetierException("Vous ne pouvez écrire que depuis votre numéro ou celui d'un agent que vous remplacez.");
        }
        String client = ValeursImport.numeroWhatsapp(demande.getNumeroClient().trim(), parametreService.valeur("whatsapp.prefixe_pays", "225"))
                .orElseThrow(() -> new ErreurMetierException("Le numéro " + demande.getNumeroClient().trim() + " n'est pas un numéro WhatsApp valide."));
        if (client.equals(agent)) {
            throw new ErreurMetierException("Le numéro du client est celui de l'expéditeur.");
        }
        return envoyer(agent, client, demande.getContenu().strip(), demande.getPieceJointeIds());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModeleMessageDTO> modeles() {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select id, module, nom, type_campagne as typeCampagne, contenu from crm_modele_message
                where canal = 'WHATSAPP' and statut = 'ACTIF' and (module = 'MARKETING' or type_campagne = 'PERSONNALISEE')
                order by module, type_campagne, nom
                """, Tuple.class).getResultList();
        return lignes.stream().map(t -> ModeleMessageDTO.builder().id(t.get("id", Long.class)).nom(t.get("nom", String.class))
                .typeCampagne(t.get("typeCampagne", String.class)).contenu(t.get("contenu", String.class)).build()).toList();
    }

    @Override
    public PieceJointeDTO deposerPieceJointe(MultipartFile fichier) {
        return pieceJointeService.deposerDocument(fichier);
    }

    /**
     * Variables d'un modele remplies avec la fiche du client du numero (colonnes du client, agence, segment), l'entreprise
     * et l'expediteur ; une variable que rien ne remplit ici (facture, promotion...) bloque l'envoi.
     */
    private String rendre(String agent, String client, String texte) {
        Set<String> codes = GabaritMessage.variables(texte);
        if (codes.isEmpty()) {
            return texte;
        }
        Map<String, String> sources = variableMessageService.sourcesParCode();
        Map<String, String> valeurs = new HashMap<>();
        Set<String> inconnues = new TreeSet<>();
        @SuppressWarnings("unchecked")
        List<Object> fiche = em.createNativeQuery("""
                select ct.client_id from crm_contact ct where ct.numero_whatsapp = :client and ct.statut = 'ACTIF'
                order by ct.fonction = 'PRINCIPAL' desc, ct.rang, ct.id limit 1
                """).setParameter("client", client).getResultList();
        Long clientId = fiche.isEmpty() ? null : ((Number) fiche.getFirst()).longValue();
        for (String code : codes) {
            String source = sources.get(code);
            String valeur = null;
            if (source == null) {
                inconnues.add(code);
                continue;
            }
            if (source.startsWith("client.") && source.substring(7).matches("[a-z][a-z0-9_]*") && clientId != null) {
                valeur = SelectionCampagne.formater(em.createNativeQuery("select `" + source.substring(7) + "` from crm_client where id = :id")
                        .setParameter("id", clientId).getSingleResult());
            } else if ("calcul.agence".equals(source) && clientId != null) {
                valeur = (String) em.createNativeQuery("select a.libelle from crm_client c join crm_agence a on a.id = c.agence_id where c.id = :id")
                        .setParameter("id", clientId).getSingleResult();
            } else if ("calcul.segment".equals(source) && clientId != null) {
                @SuppressWarnings("unchecked")
                List<String> s = em.createNativeQuery("select g.libelle from crm_client c join crm_segment g on g.code = c.segmentation where c.id = :id")
                        .setParameter("id", clientId).getResultList();
                valeur = s.isEmpty() ? "" : s.getFirst();
            } else if ("calcul.expediteur_nom".equals(source)) {
                valeur = (String) em.createNativeQuery("select nom_complet from crm_utilisateur where id = :id")
                        .setParameter("id", perimetreService.perimetreCourant().getUtilisateurId()).getSingleResult();
            } else if ("calcul.expediteur_numero".equals(source)) {
                valeur = SelectionCampagne.numeroLisible(agent);
            } else if (SelectionCampagne.SOURCES_ENTREPRISE.contains(source)) {
                valeur = SelectionCampagne.valeurEntreprise(parametreService, source);
            }
            if (valeur == null) {
                inconnues.add(code);
            } else {
                valeurs.put(code, valeur);
            }
        }
        if (!inconnues.isEmpty()) {
            throw new ErreurMetierException("Remplacez " + String.join(", ", inconnues.stream().map(c -> "{" + c + "}").toList())
                    + " avant d'envoyer : " + (clientId == null ? "ce numéro n'est pas celui d'un client, ou " : "") + "ces variables ne se remplissent que dans une campagne.");
        }
        return GabaritMessage.rendre(texte, valeurs);
    }

    /** Envoie aussitot depuis un numero connecte, garde le message et le journalise. */
    private MessageDiscussionDTO envoyer(String agent, String client, String brut, List<Long> pieceJointeIds) {
        String texte = rendre(agent, client, brut);
        List<FichierDTO> pieces = new ArrayList<>();
        if (pieceJointeIds != null) {
            if (pieceJointeIds.size() > 10) {
                throw new ErreurMetierException("Un message porte au plus 10 pièces jointes.");
            }
            pieceJointeIds.stream().distinct().forEach(id -> pieces.add(pieceJointeService.lire(id)));
        }
        @SuppressWarnings("unchecked")
        List<Object[]> sessions = em.createNativeQuery("select statut, fournisseur, meta_phone_number_id from crm_session_whatsapp where numero = :numero")
                .setParameter("numero", agent).getResultList();
        if (sessions.isEmpty() || !"CONNECTE".equals(sessions.getFirst()[0])) {
            throw new ErreurMetierException("Le numéro " + agent + " n'est pas connecté : reliez le téléphone dans Numéros WhatsApp.");
        }
        ResultatEnvoiDTO resultat;
        if ("META".equals(sessions.getFirst()[1])) {
            Number recus = (Number) em.createNativeQuery("""
                    select count(*) from crm_message_recu where numero_agent = :agent and numero_client = :client and recu_le >= utc_timestamp() - interval 23 hour
                    """).setParameter("agent", agent).setParameter("client", client).getSingleResult();
            if (recus.longValue() == 0) {
                throw new ErreurMetierException("Ce numéro passe par l'API Meta : un message libre n'est accepté que dans les 24 h qui suivent un message du client.");
            }
            String telephone = (String) sessions.getFirst()[2];
            resultat = envoyerAvecPieces(texte, pieces, t -> passerelleMeta.envoyerTexte(telephone, client, t),
                    (f, l) -> passerelleMeta.envoyerFichier(telephone, client, f, l));
        } else {
            resultat = envoyerAvecPieces(texte, pieces, t -> passerelle.envoyerTexte(agent, client, t), (f, l) -> passerelle.envoyerFichier(agent, client, f, l));
        }
        Long utilisateur = perimetreService.perimetreCourant().getUtilisateurId();
        em.createNativeQuery("""
                insert into crm_message_sortant (origine, numero_expediteur, numero_destinataire, client_id, contenu, statut, motif, identifiant_whatsapp,
                                                 tentatives, cree_par, created_at, envoye_le)
                values ('DISCUSSION', :agent, :client, (select ct.client_id from crm_contact ct where ct.numero_whatsapp = :client and ct.statut = 'ACTIF'
                                                        order by ct.fonction = 'PRINCIPAL' desc, ct.rang, ct.id limit 1),
                        :contenu, :statut, :motif, :identifiant, 1, :par, utc_timestamp(), case when :succes then utc_timestamp() end)
                """)
                .setParameter("agent", agent).setParameter("client", client).setParameter("contenu", texte)
                .setParameter("statut", resultat.isSucces() ? "ENVOYE" : "ECHEC")
                .setParameter("motif", resultat.getErreur() == null ? null : resultat.getErreur().substring(0, Math.min(255, resultat.getErreur().length())))
                .setParameter("identifiant", resultat.getIdentifiant()).setParameter("par", utilisateur).setParameter("succes", resultat.isSucces())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        journalActionService.enregistrer("DISCUSSION_REPONSE", "crm_message_sortant", id, agent + " -> " + client + (resultat.isSucces() ? "" : " : échec"));
        if (!resultat.isSucces()) {
            throw new ErreurMetierException("Le message n'est pas parti : " + resultat.getErreur());
        }
        return messages(agent, client, 1).getFirst();
    }

    /** Une 1re image porte le texte en legende s'il est court ; les autres fichiers suivent ; un fichier perdu est signale. */
    private static ResultatEnvoiDTO envoyerAvecPieces(String texte, List<FichierDTO> pieces, java.util.function.Function<String, ResultatEnvoiDTO> envoiTexte,
                                                      java.util.function.BiFunction<FichierDTO, String, ResultatEnvoiDTO> envoiFichier) {
        List<FichierDTO> reste = new ArrayList<>(pieces);
        ResultatEnvoiDTO principal = !reste.isEmpty() && reste.getFirst().getTypeContenu().startsWith("image/") && texte.length() <= 1000
                ? envoiFichier.apply(reste.removeFirst(), texte) : envoiTexte.apply(texte);
        if (!principal.isSucces()) {
            return principal;
        }
        for (FichierDTO fichier : reste) {
            ResultatEnvoiDTO piece = envoiFichier.apply(fichier, null);
            if (!piece.isSucces()) {
                principal.setErreur(fichier.getNom() + " non envoyé : " + piece.getErreur());
                break;
            }
        }
        return principal;
    }

    /** Numeros WhatsApp depuis lesquels l'utilisateur peut ecrire : le sien et ceux des agents qu'il remplace aujourd'hui. */
    private List<String> numerosUtilisables() {
        @SuppressWarnings("unchecked")
        List<String> numeros = em.createNativeQuery("""
                select u.numero_whatsapp from crm_utilisateur u where u.numero_whatsapp is not null and (u.id = :moi or u.id in (
                    select d.titulaire_id from crm_delegation d where d.remplacant_id = :moi and %s))
                order by u.id = :moi desc, u.nom_complet
                """.formatted(RequetesRecouvrement.DELEGATION_EN_COURS))
                .setParameter("moi", perimetreService.perimetreCourant().getUtilisateurId()).getResultList();
        return numeros;
    }

    private String perimetre(Map<String, Object> parametres) {
        List<Long> agentsGeres = portefeuilleService.agentsGeres();
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        // Le droit PERIMETRE_AGENCES est du Recouvrement : un directeur du seul Marketing voit aussi ses agences
        if (agentsGeres != null && !directeur(perimetre.getUtilisateurId())) {
            parametres.put("agentsGeres", agentsGeres);
            return " where u.id in (:agentsGeres)";
        }
        if (!perimetre.isVisionGlobale()) {
            parametres.put("agences", perimetre.getAgenceIds().isEmpty() ? List.of(0L) : perimetre.getAgenceIds());
            parametres.put("moi", perimetre.getUtilisateurId());
            return " where (c.agence_id in (:agences) or u.id = :moi)" + moduleDuDirecteur(perimetre.getUtilisateurId());
        }
        return " where 1 = 1";
    }

    private boolean directeur(Long moi) {
        return ((Number) em.createNativeQuery("""
                select count(*) from crm_habilitation h join crm_role r on r.id = h.role_id
                where h.utilisateur_id = :moi and h.actif and r.code in ('DIR_AGENCE', 'DIR_COMMERCIAL', 'DIR_RECOUVREMENT', 'DG', 'ADMIN')
                """).setParameter("moi", moi).getSingleResult()).longValue() > 0;
    }

    /**
     * Parametre discussions.directeur_module_seul : un directeur d'agence ne voit, hors des siennes, que les discussions
     * des agents qui ont un role dans un module ou il est directeur. Un numero d'agent sans compte reste visible.
     */
    private String moduleDuDirecteur(Long moi) {
        if (!parametreService.oui("discussions.directeur_module_seul", false)) {
            return "";
        }
        List<?> modules = em.createNativeQuery("""
                select coalesce(h.module_id, 0) from crm_habilitation h join crm_role r on r.id = h.role_id
                where h.utilisateur_id = :moi and h.actif and r.code = 'DIR_AGENCE'
                """).setParameter("moi", moi).getResultList();
        if (modules.isEmpty() || modules.stream().anyMatch(m -> ((Number) m).longValue() == 0)) {
            return "";
        }
        return " and (u.id is null or u.id = %d or exists (select 1 from crm_habilitation ha where ha.utilisateur_id = u.id and ha.actif and (ha.module_id is null or ha.module_id in (%s))))"
                .formatted(moi, modules.stream().map(m -> String.valueOf(((Number) m).longValue())).collect(Collectors.joining(",")));
    }

    private void verifierAcces(String numeroAgent, String numeroClient) {
        Map<String, Object> parametres = new LinkedHashMap<>();
        String where = perimetre(parametres) + " and d.numero_agent = :agent and d.numero_client = :client";
        parametres.put("agent", numeroAgent);
        parametres.put("client", numeroClient);
        Query requete = em.createNativeQuery("select count(*) " + DISCUSSIONS + where);
        parametres.forEach(requete::setParameter);
        if (((Number) requete.getSingleResult()).longValue() == 0) {
            throw new RessourceIntrouvableException("Discussion introuvable.");
        }
    }

    @SuppressWarnings("unchecked")
    private List<MessageDiscussionDTO> messages(String numeroAgent, String numeroClient, int nombre) {
        List<Tuple> lignes = em.createNativeQuery(FIL, Tuple.class).setParameter("agent", numeroAgent).setParameter("client", numeroClient)
                .setMaxResults(nombre).getResultList();
        return lignes.stream().map(this::mapTupleToMessage).toList();
    }

    private String nomAffiche(String numeroAgent, String numeroClient) {
        @SuppressWarnings("unchecked")
        List<String> noms = em.createNativeQuery("""
                select coalesce((select nom_affiche from crm_message_recu where numero_agent = :agent and numero_client = :client and nom_affiche is not null
                                 order by recu_le desc limit 1),
                                (select nom_destinataire from crm_message_sortant where numero_expediteur = :agent and numero_destinataire = :client
                                 and nom_destinataire is not null order by id desc limit 1))
                """).setParameter("agent", numeroAgent).setParameter("client", numeroClient).getResultList();
        return noms.isEmpty() ? null : noms.getFirst();
    }

    DiscussionDTO mapTupleToDto(Tuple tuple) {
        return DiscussionDTO.builder()
                .numeroAgent(tuple.get("numeroAgent", String.class))
                .agentNom(tuple.get("agentNom", String.class))
                .numeroClient(tuple.get("numeroClient", String.class))
                .clientId(tuple.get("clientId", Long.class))
                .clientNom(tuple.get("clientNom", String.class))
                .derniereActivite(tuple.get("derniereActivite", LocalDateTime.class))
                .nbRecus(((Number) tuple.get("nbRecus")).longValue())
                .build();
    }

    MessageDiscussionDTO mapTupleToMessage(Tuple tuple) {
        return MessageDiscussionDTO.builder()
                .cle(tuple.get("cle", String.class))
                .sens(tuple.get("sens", String.class))
                .origine(tuple.get("origine", String.class))
                .source(tuple.get("source", String.class))
                .type(tuple.get("type", String.class))
                .contenu(tuple.get("contenu", String.class))
                .statut(tuple.get("statut", String.class))
                .motif(tuple.get("motif", String.class))
                .auteur(tuple.get("auteur", String.class))
                .le(tuple.get("le", LocalDateTime.class))
                .build();
    }
}
