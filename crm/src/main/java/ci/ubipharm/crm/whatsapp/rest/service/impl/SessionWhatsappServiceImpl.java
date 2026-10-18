package ci.ubipharm.crm.whatsapp.rest.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.securite.rest.service.JournalActionService;
import ci.ubipharm.crm.securite.rest.service.PerimetreService;
import ci.ubipharm.crm.securite.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.securite.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.securite.rest.service.dto.UtilisateurPrincipal;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import ci.ubipharm.crm.whatsapp.rest.service.EvenementWhatsappService;
import ci.ubipharm.crm.whatsapp.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.whatsapp.rest.service.SessionWhatsappService;
import ci.ubipharm.crm.whatsapp.rest.service.dto.EtatSessionDTO;
import ci.ubipharm.crm.whatsapp.rest.service.dto.EvenementStatutDTO;
import ci.ubipharm.crm.whatsapp.rest.service.dto.SessionAgentDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessionWhatsappServiceImpl implements SessionWhatsappService {

    /** Profils qui lient ou delient le telephone de n'importe quel agent. */
    private static final Set<String> GESTIONNAIRES = Set.of("ROLE_ADMIN", "ROLE_DIR_RECOUVREMENT");

    private static final String SELECT = """
            select u.id as agentId, u.nom_complet as agentNom, u.numero_whatsapp as numero, coalesce(s.statut, 'DECONNECTE') as statut,
                   coalesce(s.sante, 'OK') as sante, s.raison as raison, s.connecte_le as connecteLe,
                   (select count(*) from crm_campagne_message m where m.numero_expediteur = u.numero_whatsapp and m.envoye_le >= utc_date())
                       as envoyesAujourdhui,
                   (select count(*) from crm_campagne_message m join crm_campagne c on c.id = m.campagne_id
                    where m.numero_expediteur = u.numero_whatsapp and m.statut = 'A_ENVOYER' and c.statut = 'EN_COURS') as enAttente
            from crm_utilisateur u
            left join crm_session_whatsapp s on s.numero = u.numero_whatsapp
            where u.statut = 'ACTIF' and u.numero_whatsapp is not null
            """;

    private final EntityManager em;
    private final PasserelleWhatsappService passerelle;
    private final EvenementWhatsappService evenementWhatsappService;
    private final PerimetreService perimetreService;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;

    @Override
    @Transactional(readOnly = true)
    public List<SessionAgentDTO> sessions() {
        StringBuilder where = new StringBuilder();
        Map<String, Object> parametres = new LinkedHashMap<>();
        UtilisateurPrincipal utilisateur = utilisateurCourantService.utilisateur();
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (!perimetre.isVisionGlobale()) {
            // Direction d'agence : les agents de ses agences ; agent : lui seul
            where.append(" and (u.id = :moi or exists (select 1 from crm_utilisateur_agence ua where ua.utilisateur_id = u.id"
                    + " and ua.agence_id in (:agences) and ua.date_debut <= utc_date() and (ua.date_fin is null or ua.date_fin >= utc_date())"
                    + " and :direction = true))");
            parametres.put("moi", utilisateur.getId());
            parametres.put("agences", perimetre.getAgenceIds().isEmpty() ? List.of(0L) : perimetre.getAgenceIds());
            parametres.put("direction", utilisateur.getAuthorities().stream().anyMatch(a -> "ROLE_DIR_AGENCE".equals(a.getAuthority())));
        }
        Query requete = em.createNativeQuery(SELECT + where + " order by u.nom_complet", Tuple.class);
        parametres.forEach(requete::setParameter);
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
    }

    @Override
    @Transactional
    public EtatSessionDTO connecter(Long agentId) {
        String numero = numeroAutorise(agentId);
        EtatSessionDTO etat = passerelle.demarrer(numero);
        memoriser(numero, etat);
        journalActionService.enregistrer("WHATSAPP_CONNEXION", "crm_utilisateur", agentId, numero);
        return etat;
    }

    @Override
    @Transactional
    public EtatSessionDTO etat(Long agentId) {
        String numero = numeroAutorise(agentId);
        EtatSessionDTO etat = passerelle.etat(numero);
        memoriser(numero, etat);
        return etat;
    }

    @Override
    @Transactional
    public void deconnecter(Long agentId) {
        String numero = numeroAutorise(agentId);
        passerelle.deconnecter(numero);
        memoriser(numero, EtatSessionDTO.builder().statut("DECONNECTE").sante("OK").build());
        journalActionService.enregistrer("WHATSAPP_DECONNEXION", "crm_utilisateur", agentId, numero);
    }

    private String numeroAutorise(Long agentId) {
        UtilisateurPrincipal utilisateur = utilisateurCourantService.utilisateur();
        if (!agentId.equals(utilisateur.getId()) && utilisateur.getAuthorities().stream().noneMatch(a -> GESTIONNAIRES.contains(a.getAuthority()))) {
            throw new AccessDeniedException("Seuls l'agent et la Direction du Recouvrement lient ce numero");
        }
        @SuppressWarnings("unchecked")
        List<String> numeros = em.createNativeQuery("select numero_whatsapp from crm_utilisateur where id = :id and statut = 'ACTIF'")
                .setParameter("id", agentId).getResultList();
        if (numeros.isEmpty()) {
            throw new RessourceIntrouvableException("Agent introuvable");
        }
        if (numeros.getFirst() == null) {
            throw new ErreurMetierException("Enregistrez d'abord le numero WhatsApp professionnel de l'agent.");
        }
        return numeros.getFirst();
    }

    private void memoriser(String numero, EtatSessionDTO etat) {
        evenementWhatsappService.statut(EvenementStatutDTO.builder().sessionId(numero).status(etat.getStatut()).health(etat.getSante())
                .reason(etat.getRaison()).build());
    }

    SessionAgentDTO mapTupleToDto(Tuple tuple) {
        return SessionAgentDTO.builder()
                .agentId(tuple.get("agentId", Long.class))
                .agentNom(tuple.get("agentNom", String.class))
                .numero(tuple.get("numero", String.class))
                .statut(tuple.get("statut", String.class))
                .sante(tuple.get("sante", String.class))
                .raison(tuple.get("raison", String.class))
                .connecteLe(tuple.get("connecteLe", LocalDateTime.class))
                .envoyesAujourdhui(((Number) tuple.get("envoyesAujourdhui")).longValue())
                .enAttente(((Number) tuple.get("enAttente")).longValue())
                .build();
    }
}
