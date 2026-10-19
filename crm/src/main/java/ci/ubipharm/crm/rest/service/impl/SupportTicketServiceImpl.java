package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.SupportTicketService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.SupportIntervenantDTO;
import ci.ubipharm.crm.rest.service.dto.SupportMessageDTO;
import ci.ubipharm.crm.rest.service.dto.SupportMessageDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.SupportTicketDTO;
import ci.ubipharm.crm.rest.service.dto.SupportTicketDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.UtilisateurPrincipal;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SupportTicketServiceImpl implements SupportTicketService {

    static final String DROIT_SUPPORT = "DROIT_SUPPORT";
    static final Set<String> CATEGORIES = Set.of("QUESTION", "INCIDENT", "DEMANDE");
    static final Set<String> PRIORITES = Set.of("NORMALE", "HAUTE", "URGENTE");
    static final Set<String> STATUTS = Set.of("OUVERT", "EN_COURS", "RESOLU", "FERME");
    private static final Map<String, String> LIBELLES_STATUT = Map.of("OUVERT", "Ouvert", "EN_COURS", "En cours", "RESOLU", "Résolu",
            "FERME", "Fermé");

    private static final String SELECT = """
            select t.id as id, t.sujet as sujet, t.categorie as categorie, t.priorite as priorite, t.statut as statut,
                   t.auteur_id as auteurId, a.nom_complet as auteur, t.assigne_id as assigneId, s.nom_complet as assigne,
                   (select count(*) from crm_support_message m where m.ticket_id = t.id and m.type_message = 'MESSAGE') as nbMessages,
                   (select max(m.created_at) from crm_support_message m where m.ticket_id = t.id) as dernierMessageLe,
                   t.created_at as creeLe, t.updated_at as modifieLe
            from crm_support_ticket t
            join crm_utilisateur a on a.id = t.auteur_id
            left join crm_utilisateur s on s.id = t.assigne_id
            """;

    private static final String ORDRE = """
             order by field(t.statut, 'OUVERT', 'EN_COURS', 'RESOLU', 'FERME'), field(t.priorite, 'URGENTE', 'HAUTE', 'NORMALE'),
                      coalesce(t.updated_at, t.created_at) desc, t.id desc limit 1000
            """;

    private final EntityManager em;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;

    @Override
    @Transactional(readOnly = true)
    public List<SupportTicketDTO> mesTickets(String statut) {
        Map<String, Object> parametres = new LinkedHashMap<>();
        StringBuilder where = new StringBuilder(" where t.auteur_id = :auteur");
        parametres.put("auteur", utilisateurCourantService.utilisateur().getId());
        filtrerStatut(statut, where, parametres);
        return rechercher(where.toString(), parametres);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportTicketDTO> tous(String statut, Long assigneId) {
        Map<String, Object> parametres = new LinkedHashMap<>();
        StringBuilder where = new StringBuilder(" where 1 = 1");
        filtrerStatut(statut, where, parametres);
        if (assigneId != null) {
            where.append(" and t.assigne_id = :assigne");
            parametres.put("assigne", assigneId);
        }
        return rechercher(where.toString(), parametres);
    }

    @Override
    @Transactional(readOnly = true)
    public SupportTicketDTO consulter(Long id) {
        SupportTicketDTO ticket = ticketAccessible(id);
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select m.id as id, m.type_message as type, m.auteur_id as auteurId, u.nom_complet as auteur, m.contenu as contenu,
                       m.created_at as creeLe
                from crm_support_message m
                join crm_utilisateur u on u.id = m.auteur_id
                where m.ticket_id = :id
                order by m.id
                """, Tuple.class).setParameter("id", id).getResultList();
        ticket.setMessages(lignes.stream().map(t -> mapTupleToMessage(t, ticket.getAuteurId())).toList());
        return ticket;
    }

    @Override
    @Transactional
    public SupportTicketDTO ouvrir(SupportTicketDemandeDTO demande) {
        String categorie = verifier(demande.getCategorie(), CATEGORIES, "Catégorie inconnue : ");
        String priorite = demande.getPriorite() == null || demande.getPriorite().isBlank() ? "NORMALE"
                : verifier(demande.getPriorite(), PRIORITES, "Priorité inconnue : ");
        String sujet = demande.getSujet().trim().replaceAll("\\s+", " ");
        Long auteur = utilisateurCourantService.utilisateur().getId();
        em.createNativeQuery("""
                insert into crm_support_ticket (sujet, categorie, priorite, statut, auteur_id, modifie_par, created_at)
                values (:sujet, :categorie, :priorite, 'OUVERT', :auteur, :auteur, utc_timestamp())
                """)
                .setParameter("sujet", sujet).setParameter("categorie", categorie).setParameter("priorite", priorite)
                .setParameter("auteur", auteur)
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        ajouterMessage(id, auteur, "MESSAGE", demande.getMessage().trim());
        journalActionService.enregistrer("SUPPORT_TICKET_CREATION", "crm_support_ticket", id, categorie + " " + priorite + " : " + sujet);
        return consulter(id);
    }

    @Override
    @Transactional
    public SupportTicketDTO repondre(Long id, SupportMessageDemandeDTO demande) {
        SupportTicketDTO ticket = ticketAccessible(id);
        if ("FERME".equals(ticket.getStatut())) {
            throw new ErreurMetierException("Ce ticket est fermé : ouvrez-en un nouveau.");
        }
        Long auteur = utilisateurCourantService.utilisateur().getId();
        ajouterMessage(id, auteur, "MESSAGE", demande.getContenu().trim());
        String nouveauStatut = null;
        if (auteur.equals(ticket.getAuteurId()) && "RESOLU".equals(ticket.getStatut())) {
            // L'auteur relance un ticket resolu : il revient au support
            nouveauStatut = "OUVERT";
        } else if (!auteur.equals(ticket.getAuteurId()) && "OUVERT".equals(ticket.getStatut())) {
            // Premiere reponse du support : le ticket est pris en charge
            nouveauStatut = "EN_COURS";
        }
        if (nouveauStatut != null) {
            ecrireStatut(id, nouveauStatut, auteur);
            ajouterMessage(id, auteur, "SYSTEME", "Statut : " + LIBELLES_STATUT.get(ticket.getStatut()) + " → " + LIBELLES_STATUT.get(nouveauStatut));
        } else {
            em.createNativeQuery("update crm_support_ticket set updated_at = utc_timestamp() where id = :id").setParameter("id", id).executeUpdate();
        }
        journalActionService.enregistrer("SUPPORT_TICKET_MESSAGE", "crm_support_ticket", id, ticket.getSujet());
        return consulter(id);
    }

    @Override
    @Transactional
    public SupportTicketDTO changerStatut(Long id, String statut) {
        String nouveau = verifier(statut, STATUTS, "Statut inconnu : ");
        SupportTicketDTO ticket = ticket(id);
        if (nouveau.equals(ticket.getStatut())) {
            return consulter(id);
        }
        Long par = utilisateurCourantService.utilisateur().getId();
        ecrireStatut(id, nouveau, par);
        String detail = LIBELLES_STATUT.get(ticket.getStatut()) + " → " + LIBELLES_STATUT.get(nouveau);
        ajouterMessage(id, par, "SYSTEME", "Statut : " + detail);
        journalActionService.enregistrer("SUPPORT_TICKET_STATUT", "crm_support_ticket", id, ticket.getSujet() + " : " + detail);
        return consulter(id);
    }

    @Override
    @Transactional
    public SupportTicketDTO affecter(Long id, Long utilisateurId) {
        SupportTicketDTO ticket = ticket(id);
        if ("FERME".equals(ticket.getStatut())) {
            throw new ErreurMetierException("Ce ticket est fermé.");
        }
        String nom = null;
        if (utilisateurId != null) {
            nom = intervenants().stream().filter(i -> i.getId().equals(utilisateurId)).map(SupportIntervenantDTO::getNomComplet).findFirst()
                    .orElseThrow(() -> new ErreurMetierException("Ce ticket ne peut être confié qu'à un utilisateur actif titulaire du droit Centre de support."));
        }
        if (utilisateurId == null ? ticket.getAssigneId() == null : utilisateurId.equals(ticket.getAssigneId())) {
            return consulter(id);
        }
        Long par = utilisateurCourantService.utilisateur().getId();
        em.createNativeQuery("update crm_support_ticket set assigne_id = :assigne, modifie_par = :par, updated_at = utc_timestamp() where id = :id")
                .setParameter("assigne", utilisateurId).setParameter("par", par).setParameter("id", id)
                .executeUpdate();
        String detail = nom == null ? "Ticket retiré à " + ticket.getAssigne() : "Ticket confié à " + nom;
        ajouterMessage(id, par, "SYSTEME", detail);
        journalActionService.enregistrer("SUPPORT_TICKET_AFFECTATION", "crm_support_ticket", id, ticket.getSujet() + " : " + detail);
        return consulter(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportIntervenantDTO> intervenants() {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select distinct u.id as id, u.login as login, u.nom_complet as nomComplet
                from crm_utilisateur u
                join crm_habilitation h on h.utilisateur_id = u.id and h.actif = true
                join crm_role r on r.id = h.role_id and r.actif = true
                join crm_role_droit rd on rd.role_id = r.id and rd.actif = true
                join crm_droit d on d.id = rd.droit_id and d.code = 'SUPPORT'
                where u.statut = 'ACTIF'
                order by u.nom_complet
                """, Tuple.class).getResultList();
        return lignes.stream().map(this::mapTupleToIntervenant).toList();
    }

    private void filtrerStatut(String statut, StringBuilder where, Map<String, Object> parametres) {
        if (statut != null && !statut.isBlank()) {
            where.append(" and t.statut = :statut");
            parametres.put("statut", verifier(statut, STATUTS, "Statut inconnu : "));
        }
    }

    private List<SupportTicketDTO> rechercher(String where, Map<String, Object> parametres) {
        Query requete = em.createNativeQuery(SELECT + where + ORDRE, Tuple.class);
        parametres.forEach(requete::setParameter);
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
    }

    private SupportTicketDTO ticket(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where t.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Ticket introuvable");
        }
        return mapTupleToDto(lignes.getFirst());
    }

    /** Un ticket ne se lit et ne recoit de message que de son auteur ou d'un titulaire du droit Support. */
    private SupportTicketDTO ticketAccessible(Long id) {
        SupportTicketDTO ticket = ticket(id);
        UtilisateurPrincipal utilisateur = utilisateurCourantService.utilisateur();
        boolean support = utilisateur.getAuthorities().stream().anyMatch(a -> DROIT_SUPPORT.equals(a.getAuthority()));
        if (!support && !utilisateur.getId().equals(ticket.getAuteurId())) {
            throw new AccessDeniedException("Ticket d'un autre utilisateur");
        }
        return ticket;
    }

    private void ecrireStatut(Long id, String statut, Long par) {
        em.createNativeQuery("""
                update crm_support_ticket set statut = :statut, modifie_par = :par, updated_at = utc_timestamp(),
                       resolu_le = case when :statut = 'RESOLU' then utc_timestamp() else resolu_le end,
                       ferme_le = case when :statut = 'FERME' then utc_timestamp() else ferme_le end
                where id = :id
                """)
                .setParameter("statut", statut).setParameter("par", par).setParameter("id", id)
                .executeUpdate();
    }

    private void ajouterMessage(Long ticketId, Long auteurId, String type, String contenu) {
        em.createNativeQuery("""
                insert into crm_support_message (ticket_id, auteur_id, type_message, contenu, created_at)
                values (:ticket, :auteur, :type, :contenu, utc_timestamp())
                """)
                .setParameter("ticket", ticketId).setParameter("auteur", auteurId).setParameter("type", type).setParameter("contenu", contenu)
                .executeUpdate();
    }

    private static String verifier(String valeur, Set<String> permises, String erreur) {
        String v = valeur == null ? "" : valeur.trim().toUpperCase(Locale.ROOT);
        if (!permises.contains(v)) {
            throw new ErreurMetierException(erreur + valeur + ".");
        }
        return v;
    }

    SupportTicketDTO mapTupleToDto(Tuple tuple) {
        return SupportTicketDTO.builder()
                .id(((Number) tuple.get("id")).longValue())
                .sujet(tuple.get("sujet", String.class))
                .categorie(tuple.get("categorie", String.class))
                .priorite(tuple.get("priorite", String.class))
                .statut(tuple.get("statut", String.class))
                .auteurId(((Number) tuple.get("auteurId")).longValue())
                .auteur(tuple.get("auteur", String.class))
                .assigneId(tuple.get("assigneId") == null ? null : ((Number) tuple.get("assigneId")).longValue())
                .assigne(tuple.get("assigne", String.class))
                .nbMessages(((Number) tuple.get("nbMessages")).longValue())
                .dernierMessageLe(tuple.get("dernierMessageLe", LocalDateTime.class))
                .creeLe(tuple.get("creeLe", LocalDateTime.class))
                .modifieLe(tuple.get("modifieLe", LocalDateTime.class))
                .build();
    }

    SupportMessageDTO mapTupleToMessage(Tuple tuple, Long auteurTicket) {
        Long auteurId = ((Number) tuple.get("auteurId")).longValue();
        return SupportMessageDTO.builder()
                .id(((Number) tuple.get("id")).longValue())
                .type(tuple.get("type", String.class))
                .auteurId(auteurId)
                .auteur(tuple.get("auteur", String.class))
                .support(!auteurId.equals(auteurTicket))
                .contenu(tuple.get("contenu", String.class))
                .creeLe(tuple.get("creeLe", LocalDateTime.class))
                .build();
    }

    SupportIntervenantDTO mapTupleToIntervenant(Tuple tuple) {
        return SupportIntervenantDTO.builder()
                .id(((Number) tuple.get("id")).longValue())
                .login(tuple.get("login", String.class))
                .nomComplet(tuple.get("nomComplet", String.class))
                .build();
    }
}
