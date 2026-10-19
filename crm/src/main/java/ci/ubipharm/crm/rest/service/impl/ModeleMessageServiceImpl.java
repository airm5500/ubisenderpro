package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.ModeleMessageService;
import ci.ubipharm.crm.rest.service.VariableMessageService;
import ci.ubipharm.crm.rest.service.dto.ModeleMessageCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.ModeleMessageDTO;
import ci.ubipharm.crm.rest.service.dto.ModeleMessageDemandeDTO;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

/**
 * Modeles de messages : la Direction du Recouvrement ecrit ceux des relances, les agents ne font que les utiliser (4.8) ;
 * le Marketing ecrit ses modeles promotionnels, qui ne citent ni facture ni agent de recouvrement.
 */
@Service
@RequiredArgsConstructor
public class ModeleMessageServiceImpl implements ModeleMessageService {

    private static final String SELECT = """
            select m.id as id, m.nom as nom, m.type_campagne as typeCampagne, m.canal as canal, m.objet as objet, m.contenu as contenu, m.statut as statut,
                   c.nom_complet as creePar, u.nom_complet as modifiePar, m.created_at as createdAt, m.updated_at as updatedAt
            from crm_modele_message m
            join crm_utilisateur c on c.id = m.cree_par
            left join crm_utilisateur u on u.id = m.modifie_par
            """;

    /** Sources utilisables sans facture ni agent de recouvrement. */
    private static final Set<String> SOURCES_MARKETING = Set.of("calcul.agence", "calcul.segment", "calcul.expediteur_nom", "calcul.expediteur_numero",
            "calcul.promotion_nom", "calcul.promotion_code", "calcul.promotion_debut", "calcul.promotion_fin", "calcul.produits_promotion");

    /** 4 SMS de 153 caracteres. */
    private static final int SMS_LONGUEUR_MAX = 612;

    private final EntityManager em;
    private final VariableMessageService variableMessageService;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;

    @Override
    @Transactional(readOnly = true)
    public List<ModeleMessageDTO> rechercher(String module, ModeleMessageCriteresDTO criteres) {
        StringBuilder where = new StringBuilder(" where m.module = :module and m.statut = :statut");
        Map<String, Object> parametres = new LinkedHashMap<>();
        parametres.put("module", module);
        parametres.put("statut", criteres.getStatut() == null ? "ACTIF" : criteres.getStatut());
        if (criteres.getTypeCampagne() != null) {
            where.append(" and m.type_campagne = :typeCampagne");
            parametres.put("typeCampagne", criteres.getTypeCampagne());
        }
        if (criteres.getCanal() != null) {
            where.append(" and m.canal = :canal");
            parametres.put("canal", criteres.getCanal());
        }
        Query requete = em.createNativeQuery(SELECT + where + " order by m.type_campagne, m.nom", Tuple.class);
        parametres.forEach(requete::setParameter);
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ModeleMessageDTO consulter(String module, Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where m.id = :id and m.module = :module", Tuple.class)
                .setParameter("id", id).setParameter("module", module).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Modèle introuvable");
        }
        return mapTupleToDto(lignes.getFirst());
    }

    @Override
    @Transactional
    public ModeleMessageDTO creer(String module, ModeleMessageDemandeDTO demande) {
        verifier(module, demande, null);
        em.createNativeQuery("""
                insert into crm_modele_message (module, nom, type_campagne, canal, objet, contenu, cree_par, created_at)
                values (:module, :nom, :typeCampagne, :canal, :objet, :contenu, :creePar, utc_timestamp())
                """)
                .setParameter("module", module)
                .setParameter("nom", demande.getNom().trim())
                .setParameter("typeCampagne", type(module, demande))
                .setParameter("canal", canal(demande))
                .setParameter("objet", objet(demande))
                .setParameter("contenu", demande.getContenu())
                .setParameter("creePar", utilisateurCourantService.utilisateur().getId())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        journalActionService.enregistrer("MODELE_CREATION", "crm_modele_message", id, demande.getNom().trim());
        return consulter(module, id);
    }

    @Override
    @Transactional
    public ModeleMessageDTO modifier(String module, Long id, ModeleMessageDemandeDTO demande) {
        ModeleMessageDTO existant = consulter(module, id);
        if ("ARCHIVE".equals(existant.getStatut())) {
            throw new ErreurMetierException("Le modèle « " + existant.getNom() + " » est archivé.");
        }
        verifier(module, demande, id);
        if (!existant.getCanal().equals(canal(demande))) {
            Number utilisations = (Number) em.createNativeQuery("select count(*) from crm_campagne where modele_id = :id")
                    .setParameter("id", id).getSingleResult();
            if (utilisations.longValue() > 0) {
                throw new ErreurMetierException("Ce modèle sert déjà à des campagnes : son canal ne change plus. Créez un autre modèle.");
            }
        }
        em.createNativeQuery("""
                update crm_modele_message set nom = :nom, type_campagne = :typeCampagne, canal = :canal, objet = :objet, contenu = :contenu,
                       modifie_par = :modifiePar, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("nom", demande.getNom().trim())
                .setParameter("typeCampagne", type(module, demande))
                .setParameter("canal", canal(demande))
                .setParameter("objet", objet(demande))
                .setParameter("contenu", demande.getContenu())
                .setParameter("modifiePar", utilisateurCourantService.utilisateur().getId())
                .setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("MODELE_MODIFICATION", "crm_modele_message", id, demande.getNom().trim());
        return consulter(module, id);
    }

    @Override
    @Transactional
    public ModeleMessageDTO archiver(String module, Long id) {
        ModeleMessageDTO modele = consulter(module, id);
        @SuppressWarnings("unchecked")
        List<String> campagnes = em.createNativeQuery("""
                select nom from crm_campagne where modele_id = :id and statut in ('BROUILLON', 'A_VALIDER') order by nom
                """).setParameter("id", id).getResultList();
        if (!campagnes.isEmpty()) {
            throw new ErreurMetierException("Le modèle est utilisé par des campagnes non validées : " + String.join(", ", campagnes) + ".");
        }
        em.createNativeQuery("update crm_modele_message set statut = 'ARCHIVE', modifie_par = :modifiePar, updated_at = utc_timestamp() where id = :id")
                .setParameter("modifiePar", utilisateurCourantService.utilisateur().getId())
                .setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("MODELE_ARCHIVAGE", "crm_modele_message", id, modele.getNom());
        return consulter(module, id);
    }

    private void verifier(String module, ModeleMessageDemandeDTO demande, Long id) {
        if (!"MARKETING".equals(module) && !GabaritMessage.TYPES_CAMPAGNE.contains(demande.getTypeCampagne())) {
            throw new ErreurMetierException("Type de relance inconnu : " + demande.getTypeCampagne() + ".");
        }
        if ("EMAIL".equals(canal(demande)) && objet(demande) == null) {
            throw new ErreurMetierException("Saisissez l'objet de l'e-mail.");
        }
        // Au-dela, le message serait decoupe en trop de SMS factures (153 caracteres par SMS, 67 avec des caracteres speciaux)
        if ("SMS".equals(canal(demande)) && demande.getContenu() != null && demande.getContenu().length() > SMS_LONGUEUR_MAX) {
            throw new ErreurMetierException("Un modèle SMS tient en " + SMS_LONGUEUR_MAX + " caractères, variables comprises.");
        }
        Map<String, String> connues = variableMessageService.sourcesParCode();
        List<String> inconnues = new ArrayList<>();
        Set<String> utilisees = new LinkedHashSet<>(GabaritMessage.variables(demande.getContenu()));
        utilisees.addAll(GabaritMessage.variables(Objects.requireNonNullElse(objet(demande), "")));
        utilisees.stream().filter(v -> !connues.containsKey(v)).forEach(v -> inconnues.add("{" + v + "}"));
        if (!inconnues.isEmpty()) {
            throw new ErreurMetierException((inconnues.size() == 1 ? "Variable inconnue : " : "Variables inconnues : ") + String.join(", ", inconnues) + ".");
        }
        if ("MARKETING".equals(module)) {
            List<String> horsMarketing = utilisees.stream().filter(v -> !sourceMarketing(connues.get(v))).map(v -> "{" + v + "}").toList();
            if (!horsMarketing.isEmpty()) {
                throw new ErreurMetierException("Une promotion ne porte sur aucune facture ni agent de recouvrement : retirez "
                        + String.join(", ", horsMarketing) + ".");
            }
        } else {
            List<String> promotions = utilisees.stream().filter(v -> sourcePromotion(connues.get(v))).map(v -> "{" + v + "}").toList();
            if (!promotions.isEmpty()) {
                throw new ErreurMetierException("Une relance ne cite aucune promotion : retirez " + String.join(", ", promotions) + ".");
            }
        }
        // Le nom reste unique tous modules confondus : les journaux et les campagnes le citent
        long homonymes = ((Number) em.createNativeQuery("select count(*) from crm_modele_message where nom = :nom and id <> :id")
                .setParameter("nom", demande.getNom().trim())
                .setParameter("id", id == null ? 0L : id)
                .getSingleResult()).longValue();
        if (homonymes > 0) {
            throw new ErreurMetierException("Un modèle s'appelle déjà « " + demande.getNom().trim() + " ».");
        }
    }

    /** Variables d'une promotion : celles du client et de l'expediteur. */
    static boolean sourceMarketing(String source) {
        return source != null && (source.startsWith("client.") || SOURCES_MARKETING.contains(source));
    }

    /** Variables remplies par les promotions qu'une campagne Marketing cite. */
    static boolean sourcePromotion(String source) {
        return source != null && (source.startsWith("calcul.promotion_") || "calcul.produits_promotion".equals(source));
    }

    private static String type(String module, ModeleMessageDemandeDTO demande) {
        return "MARKETING".equals(module) ? "PROMOTION" : demande.getTypeCampagne();
    }

    private static String canal(ModeleMessageDemandeDTO demande) {
        return demande.getCanal() == null || demande.getCanal().isBlank() ? "WHATSAPP" : demande.getCanal();
    }

    /** L'objet n'a de sens que pour un e-mail. */
    private static String objet(ModeleMessageDemandeDTO demande) {
        return "EMAIL".equals(canal(demande)) && demande.getObjet() != null && !demande.getObjet().isBlank() ? demande.getObjet().trim() : null;
    }

    ModeleMessageDTO mapTupleToDto(Tuple tuple) {
        String contenu = tuple.get("contenu", String.class);
        return ModeleMessageDTO.builder()
                .id(tuple.get("id", Long.class))
                .nom(tuple.get("nom", String.class))
                .typeCampagne(tuple.get("typeCampagne", String.class))
                .canal(tuple.get("canal", String.class))
                .objet(tuple.get("objet", String.class))
                .contenu(contenu)
                .statut(tuple.get("statut", String.class))
                .variables(List.copyOf(GabaritMessage.variables(contenu)))
                .creePar(tuple.get("creePar", String.class))
                .modifiePar(tuple.get("modifiePar", String.class))
                .createdAt(tuple.get("createdAt", LocalDateTime.class))
                .updatedAt(tuple.get("updatedAt", LocalDateTime.class))
                .build();
    }
}
