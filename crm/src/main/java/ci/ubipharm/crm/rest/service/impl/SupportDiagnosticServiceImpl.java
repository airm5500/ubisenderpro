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
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.SupportErreurDTO;
import ci.ubipharm.crm.rest.service.dto.SupportErreurNavigateurDTO;
import ci.ubipharm.crm.rest.service.dto.UtilisateurPrincipal;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SupportDiagnosticServiceImpl implements SupportDiagnosticService {

    private static final String SELECT = """
            select e.id as id, e.signature as signature, e.origine as origine, e.type_erreur as type, e.message as message,
                   e.detail as detail, e.chemin as chemin, u.nom_complet as utilisateur, e.occurrences as occurrences,
                   e.premiere_le as premiereLe, e.derniere_le as derniereLe, e.traite as traite, tp.nom_complet as traitePar,
                   e.traite_le as traiteLe
            from crm_support_erreur e
            left join crm_utilisateur u on u.id = e.utilisateur_id
            left join crm_utilisateur tp on tp.id = e.traite_par
            """;

    private static final int MAX_DETAIL = 20000;
    /** Au-dela, la table des compteurs est videe : elle ne sert qu'a la minute en cours. */
    private static final int MAX_SIGNATURES_SUIVIES = 2000;

    private final EntityManager em;
    private final ParametreService parametreService;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;
    private final TransactionTemplate transactionSeparee;

    /** Fenetre de chaque signature : [debut en millisecondes, nombre d'enregistrements]. */
    private final Map<String, long[]> compteurs = new ConcurrentHashMap<>();

    public SupportDiagnosticServiceImpl(EntityManager em, ParametreService parametreService, UtilisateurCourantService utilisateurCourantService,
            JournalActionService journalActionService, PlatformTransactionManager transactions) {
        this.em = em;
        this.parametreService = parametreService;
        this.utilisateurCourantService = utilisateurCourantService;
        this.journalActionService = journalActionService;
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
            enregistrer("SERVEUR", erreur.getClass().getName(), erreur.getMessage(), detail, chemin);
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
            enregistrer("NAVIGATEUR", type, erreur.getMessage(), erreur.getPile(), erreur.getUrl());
        } catch (Throwable t) {
            log.warn("Capture d'erreur navigateur impossible : {}", t.toString());
        }
    }

    /** Insertion ou increment du compteur de la signature, dans sa propre transaction ; les repetitions au-dela du plafond sont ignorees. */
    private void enregistrer(String origine, String type, String message, String detail, String chemin) {
        String typeCourt = tronquer(type, 255);
        String signature = signature(typeCourt, message);
        Long utilisateurId = utilisateurConnecte();
        transactionSeparee.executeWithoutResult(statut -> {
            int plafond = parametreService.entier("support.capture_max_minute", 10);
            if (!autorise(signature, plafond)) {
                return;
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
        });
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
    public List<SupportErreurDTO> lister(boolean traitees) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where e.traite = :traite order by e.derniere_le desc, e.id desc limit 500", Tuple.class)
                .setParameter("traite", traitees).getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
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

    private SupportErreurDTO consulter(Long id) {
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
                .build();
    }
}
