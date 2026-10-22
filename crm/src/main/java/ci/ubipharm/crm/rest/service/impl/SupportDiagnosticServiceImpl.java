package ci.ubipharm.crm.rest.service.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.SupportDiagnosticService;
import ci.ubipharm.crm.rest.service.SupportTicketService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.SupportErreurDTO;
import ci.ubipharm.crm.rest.service.dto.SupportErreurNavigateurDTO;
import ci.ubipharm.crm.rest.service.dto.SupportTicketDTO;
import ci.ubipharm.crm.rest.service.dto.UtilisateurPrincipal;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import ci.ubipharm.crm.socle.support.NotificationSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SupportDiagnosticServiceImpl implements SupportDiagnosticService {

    private static final String SELECT = """
            select e.id as id, e.signature as signature, e.origine as origine, e.type_erreur as type, e.message as message,
                   e.detail as detail, e.chemin as chemin, u.nom_complet as utilisateur, e.occurrences as occurrences,
                   e.premiere_le as premiereLe, e.derniere_le as derniereLe, e.traite as traite, tp.nom_complet as traitePar,
                   e.traite_le as traiteLe, e.ticket_id as ticketId, t.statut as ticketStatut
            from crm_support_erreur e
            left join crm_support_ticket t on t.id = e.ticket_id
            left join crm_utilisateur u on u.id = e.utilisateur_id
            left join crm_utilisateur tp on tp.id = e.traite_par
            """;

    static final Set<String> ORIGINES = Set.of("SERVEUR", "NAVIGATEUR", "LENTEUR", "SURVEILLANCE");
    private static final Map<String, String> LIBELLES_ORIGINE = Map.of("SERVEUR", "serveur", "NAVIGATEUR", "navigateur", "LENTEUR", "lenteur",
            "SURVEILLANCE", "surveillance du serveur");
    private static final int MAX_DETAIL = 20000;
    /** Au-dela, la table des compteurs est videe : elle ne sert qu'a la minute en cours. */
    private static final int MAX_SIGNATURES_SUIVIES = 2000;

    private final EntityManager em;
    private final ParametreService parametreService;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;
    private final SupportTicketService supportTicketService;
    private final NotificationSupport notificationSupport;
    private final TransactionTemplate transactionSeparee;

    /** Fenetre de chaque signature : [debut en millisecondes, nombre d'enregistrements]. */
    private final Map<String, long[]> compteurs = new ConcurrentHashMap<>();

    public SupportDiagnosticServiceImpl(EntityManager em, ParametreService parametreService, UtilisateurCourantService utilisateurCourantService,
            JournalActionService journalActionService, SupportTicketService supportTicketService, NotificationSupport notificationSupport,
            PlatformTransactionManager transactions) {
        this.em = em;
        this.parametreService = parametreService;
        this.utilisateurCourantService = utilisateurCourantService;
        this.journalActionService = journalActionService;
        this.supportTicketService = supportTicketService;
        this.notificationSupport = notificationSupport;
        this.transactionSeparee = new TransactionTemplate(transactions);
        this.transactionSeparee.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void capturerServeur(Throwable erreur, String reference) {
        try {
            String chemin = null;
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributs) {
                chemin = attributs.getRequest().getMethod() + " " + attributs.getRequest().getRequestURI();
            }
            StringWriter pile = new StringWriter();
            erreur.printStackTrace(new PrintWriter(pile));
            String detail = (reference == null ? "" : "Référence " + reference + "\n") + pile;
            enregistrer("SERVEUR", erreur.getClass().getName(), erreur.getMessage(), detail, chemin, utilisateurConnecte());
        } catch (Throwable t) {
            log.warn("Capture d'erreur serveur impossible : {}", t.toString());
        }
    }

    @Override
    public void capturerNavigateur(SupportErreurNavigateurDTO erreur) {
        try {
            if (erreur == null) {
                return;
            }
            String type = erreur.getType() == null || erreur.getType().isBlank() ? "Error" : erreur.getType().trim();
            enregistrer("NAVIGATEUR", type, erreur.getMessage(), erreur.getPile(), erreur.getUrl(), utilisateurConnecte());
        } catch (Throwable t) {
            log.warn("Capture d'erreur navigateur impossible : {}", t.toString());
        }
    }

    @Override
    public void signaler(String origine, String type, String message, String detail, String chemin, Long utilisateurId) {
        try {
            if (!ORIGINES.contains(origine)) {
                throw new IllegalArgumentException("Origine inconnue : " + origine);
            }
            enregistrer(origine, type, message, detail, chemin, utilisateurId);
        } catch (Throwable t) {
            log.warn("Signalement {} impossible : {}", origine, t.toString());
        }
    }

    /**
     * Insertion ou increment du compteur de la signature, dans sa propre transaction ; les repetitions au-dela du plafond sont ignorees.
     * Le ticket automatique s'ouvre dans la meme transaction ; l'e-mail qui le signale part ensuite, en arriere-plan.
     */
    private void enregistrer(String origine, String type, String message, String detail, String chemin, Long utilisateurId) {
        String typeCourt = tronquer(type, 255);
        String signature = signature(typeCourt, message);
        Long ticket = transactionSeparee.execute(statut -> {
            int plafond = parametreService.entier("support.capture_max_minute", 10);
            if (!autorise(signature, plafond)) {
                return null;
            }
            em.createNativeQuery("""
                    insert into crm_support_erreur (signature, origine, type_erreur, message, detail, chemin, utilisateur_id, occurrences,
                                                    premiere_le, derniere_le, traite)
                    values (:signature, :origine, :type, :message, :detail, :chemin, :utilisateur, 1, utc_timestamp(), utc_timestamp(), false)
                    on duplicate key update occurrences = occurrences + 1, derniere_le = utc_timestamp(), message = values(message),
                                            detail = values(detail), chemin = values(chemin), utilisateur_id = values(utilisateur_id),
                                            traite = false, traite_par = null, traite_le = null
                    """)
                    .setParameter("signature", signature).setParameter("origine", origine).setParameter("type", typeCourt)
                    .setParameter("message", tronquer(message, 2000)).setParameter("detail", tronquer(detail, MAX_DETAIL))
                    .setParameter("chemin", tronquer(chemin, 500)).setParameter("utilisateur", utilisateurId)
                    .executeUpdate();
            return ticketAutomatique(signature);
        });
        if (ticket != null) {
            notificationSupport.ticketAutomatique(ticket);
        }
    }

    /**
     * Ticket automatique : des la 1re survenue d'une erreur du serveur ou d'une alerte de la surveillance, a partir du seuil pour
     * une erreur du navigateur, jamais pour une simple lenteur. Une erreur dont le ticket est en cours n'en ouvre pas d'autre ;
     * celle d'un ticket resolu le rouvre. Plafond quotidien contre les rafales. Renvoie le ticket ouvert, null sinon.
     */
    private Long ticketAutomatique(String signature) {
        if (!parametreService.oui("support.ticket_auto", true)) {
            return null;
        }
        SupportErreurDTO erreur = mapTupleToDto((Tuple) em.createNativeQuery(SELECT + " where e.signature = :signature", Tuple.class)
                .setParameter("signature", signature).getSingleResult());
        if (erreur.getTicketId() != null && !"FERME".equals(erreur.getTicketStatut())) {
            if ("RESOLU".equals(erreur.getTicketStatut())) {
                supportTicketService.rouvrirPourErreur(erreur.getTicketId(), erreur.getOccurrences());
            }
            return null;
        }
        int seuil = switch (erreur.getOrigine()) {
            case "SERVEUR", "SURVEILLANCE" -> 1;
            case "NAVIGATEUR" -> parametreService.entier("support.ticket_auto_seuil_navigateur", 5);
            default -> Integer.MAX_VALUE;
        };
        if (erreur.getOccurrences() < seuil) {
            return null;
        }
        long aujourdhui = ((Number) em.createNativeQuery(
                "select count(*) from crm_support_ticket where auteur_id is null and created_at >= utc_date()").getSingleResult()).longValue();
        if (aujourdhui >= parametreService.entier("support.ticket_auto_max_jour", 10)) {
            return null;
        }
        return supportTicketService.ouvrirPourErreur(erreur.getId(), sujet(erreur), contenu(erreur, true), null);
    }

    static String sujet(SupportErreurDTO erreur) {
        String type = erreur.getType().contains(".") && !erreur.getType().contains(" ")
                ? erreur.getType().substring(erreur.getType().lastIndexOf('.') + 1) : erreur.getType();
        String message = erreur.getMessage() == null || erreur.getMessage().isBlank() ? "" : " : " + erreur.getMessage().replaceAll("\\s+", " ");
        return "[Auto] " + type + message;
    }

    private static String contenu(SupportErreurDTO erreur, boolean automatique) {
        StringBuilder texte = new StringBuilder(automatique ? "Ticket ouvert automatiquement par le diagnostic du centre de support.\n\n" : "");
        texte.append("Origine : ").append(LIBELLES_ORIGINE.getOrDefault(erreur.getOrigine(), erreur.getOrigine())).append('\n');
        texte.append("Erreur : ").append(erreur.getType()).append('\n');
        if (erreur.getMessage() != null && !erreur.getMessage().isBlank()) {
            texte.append("Message : ").append(erreur.getMessage()).append('\n');
        }
        if (erreur.getChemin() != null) {
            texte.append("Écran ou adresse : ").append(erreur.getChemin()).append('\n');
        }
        if (erreur.getUtilisateur() != null) {
            texte.append("Utilisateur concerné : ").append(erreur.getUtilisateur()).append('\n');
        }
        texte.append("Survenues : ").append(erreur.getOccurrences()).append('\n');
        texte.append("\nLe détail technique se lit dans Centre de support, onglet Diagnostic.");
        return texte.toString();
    }

    /** Limite le nombre d'enregistrements d'une meme signature sur une fenetre d'une minute ouverte par sa premiere survenue. */
    boolean autorise(String signature, int plafond) {
        long maintenant = System.currentTimeMillis();
        if (compteurs.size() > MAX_SIGNATURES_SUIVIES) {
            compteurs.clear();
        }
        long[] c = compteurs.compute(signature, (cle, v) -> v == null || maintenant - v[0] >= 60000 ? new long[] {maintenant, 1}
                : new long[] {v[0], v[1] + 1});
        return c[1] <= plafond;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportErreurDTO> lister(boolean traitees, String origine) {
        boolean filtre = origine != null && !origine.isBlank();
        if (filtre && !ORIGINES.contains(origine)) {
            throw new ErreurMetierException("Origine inconnue : " + origine + ".");
        }
        Query requete = em.createNativeQuery(SELECT + " where e.traite = :traite" + (filtre ? " and e.origine = :origine" : "")
                + " order by e.derniere_le desc, e.id desc limit 500", Tuple.class).setParameter("traite", traitees);
        if (filtre) {
            requete.setParameter("origine", origine);
        }
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
    }

    @Override
    @Transactional
    public SupportTicketDTO creerTicket(Long id) {
        SupportErreurDTO erreur = consulter(id);
        if (erreur.getTicketId() != null && !"FERME".equals(erreur.getTicketStatut())) {
            throw new ErreurMetierException("Cette erreur a déjà son ticket, le n° " + erreur.getTicketId() + ".");
        }
        Long ticket = supportTicketService.ouvrirPourErreur(id, sujet(erreur).replaceFirst("^\\[Auto\\] ", ""), contenu(erreur, false),
                utilisateurCourantService.utilisateur().getId());
        return supportTicketService.consulter(ticket);
    }

    @Override
    @Transactional
    public SupportErreurDTO traiter(Long id) {
        SupportErreurDTO erreur = consulter(id);
        if (erreur.isTraite()) {
            return erreur;
        }
        em.createNativeQuery("update crm_support_erreur set traite = true, traite_par = :par, traite_le = utc_timestamp() where id = :id")
                .setParameter("par", utilisateurCourantService.utilisateur().getId()).setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("SUPPORT_ERREUR_TRAITEMENT", "crm_support_erreur", id,
                erreur.getType() + " (" + erreur.getOccurrences() + " survenue(s))");
        return consulter(id);
    }

    @Override
    @Transactional
    public int purger() {
        int jours = parametreService.entier("support.retention_jours", 90);
        int n = em.createNativeQuery("delete from crm_support_erreur where derniere_le < utc_timestamp() - interval :jours day")
                .setParameter("jours", jours).executeUpdate();
        if (n > 0) {
            journalActionService.enregistrerSysteme("SUPPORT_ERREUR_PURGE", "crm_support_erreur", null,
                    n + " erreur(s) sans survenue depuis " + jours + " jours");
        }
        return n;
    }

    @Override
    @Transactional(readOnly = true)
    public SupportErreurDTO consulter(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where e.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Erreur introuvable");
        }
        return mapTupleToDto(lignes.getFirst());
    }

    private static Long utilisateurConnecte() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        return authentification != null && authentification.getPrincipal() instanceof UtilisateurPrincipal u ? u.getId() : null;
    }

    /** Message sans chiffres (identifiants, montants, dates) ni espaces multiples : deux survenues de la meme erreur se rejoignent. */
    static String normaliser(String message) {
        if (message == null) {
            return "";
        }
        String s = message.replaceAll("\\d+", "").replaceAll("\\s+", " ").trim();
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    /** Empreinte SHA-256 du type et du message normalise. */
    static String signature(String type, String message) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] empreinte = sha.digest(((type == null ? "" : type) + "|" + normaliser(message)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(empreinte);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String tronquer(String valeur, int max) {
        return valeur == null || valeur.length() <= max ? valeur : valeur.substring(0, max);
    }

    SupportErreurDTO mapTupleToDto(Tuple tuple) {
        return SupportErreurDTO.builder()
                .id(((Number) tuple.get("id")).longValue())
                .signature(tuple.get("signature", String.class))
                .origine(tuple.get("origine", String.class))
                .type(tuple.get("type", String.class))
                .message(tuple.get("message", String.class))
                .detail(tuple.get("detail", String.class))
                .chemin(tuple.get("chemin", String.class))
                .utilisateur(tuple.get("utilisateur", String.class))
                .occurrences(((Number) tuple.get("occurrences")).longValue())
                .premiereLe(tuple.get("premiereLe", LocalDateTime.class))
                .derniereLe(tuple.get("derniereLe", LocalDateTime.class))
                .traite(Boolean.TRUE.equals(tuple.get("traite", Boolean.class)))
                .traitePar(tuple.get("traitePar", String.class))
                .traiteLe(tuple.get("traiteLe", LocalDateTime.class))
                .ticketId(tuple.get("ticketId") == null ? null : ((Number) tuple.get("ticketId")).longValue())
                .ticketStatut(tuple.get("ticketStatut", String.class))
                .build();
    }
}
