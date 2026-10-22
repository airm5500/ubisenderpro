package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.rest.service.dto.UtilisateurPrincipal;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import ci.ubipharm.crm.rest.service.EvenementWhatsappService;
import ci.ubipharm.crm.rest.service.PasserelleMetaService;
import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.rest.service.SessionWhatsappService;
import ci.ubipharm.crm.rest.service.dto.EtatSessionDTO;
import ci.ubipharm.crm.rest.service.dto.EvenementStatutDTO;
import ci.ubipharm.crm.rest.service.dto.SessionAgentDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessionWhatsappServiceImpl implements SessionWhatsappService {

    /** Profils qui lient ou delient le telephone de n'importe quel agent. */
    private static final Set<String> GESTIONNAIRES = Set.of("DROIT_NUMEROS_TOUS");

    private static final String SELECT = """
            select u.id as agentId, u.nom_complet as agentNom, u.numero_whatsapp as numero, coalesce(s.statut, 'DECONNECTE') as statut,
                   coalesce(s.fournisseur, 'WA_WEB') as fournisseur, s.meta_phone_number_id as metaPhoneNumberId,
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
    private final PasserelleMetaService passerelleMeta;
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
            parametres.put("direction", utilisateur.getAuthorities().stream().anyMatch(a -> "DROIT_PERIMETRE_AGENCES".equals(a.getAuthority())));
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
        String phoneNumberId = phoneNumberIdMeta(numero);
        // Un numero de l'API officielle n'a pas de QR : on verifie qu'il repond chez Meta
        EtatSessionDTO etat = phoneNumberId != null
                ? EtatSessionDTO.builder().statut("CONNECTE").sante("OK").raison(passerelleMeta.verifierNumero(phoneNumberId)).build()
                : passerelle.demarrer(numero);
        memoriser(numero, etat);
        journalActionService.enregistrer("WHATSAPP_CONNEXION", "crm_utilisateur", agentId, numero);
        return etat;
    }

    @Override
    @Transactional
    public EtatSessionDTO etat(Long agentId) {
        String numero = numeroAutorise(agentId);
        if (phoneNumberIdMeta(numero) != null) {
            return etatEnBase(numero);
        }
        EtatSessionDTO etat = passerelle.etat(numero);
        memoriser(numero, etat);
        return etat;
    }

    @Override
    @Transactional
    public void deconnecter(Long agentId) {
        String numero = numeroAutorise(agentId);
        if (phoneNumberIdMeta(numero) == null) {
            passerelle.deconnecter(numero);
        }
        memoriser(numero, EtatSessionDTO.builder().statut("DECONNECTE").sante("OK").build());
        journalActionService.enregistrer("WHATSAPP_DECONNEXION", "crm_utilisateur", agentId, numero);
    }

    @Override
    @Transactional
    public SessionAgentDTO passerMeta(Long agentId, String phoneNumberId) {
        String numero = numeroAutorise(agentId);
        String autre = (String) em.createNativeQuery("select max(numero) from crm_session_whatsapp where meta_phone_number_id = :id and numero <> :numero")
                .setParameter("id", phoneNumberId).setParameter("numero", numero).getSingleResult();
        if (autre != null) {
            throw new ErreurMetierException("Cet identifiant Meta est déjà celui du numéro " + SelectionCampagne.numeroLisible(autre) + ".");
        }
        String verifie = passerelleMeta.verifierNumero(phoneNumberId);
        if (phoneNumberIdMeta(numero) == null) {
            // Un seul canal d'envoi par numero : la session WhatsApp Web du telephone est fermee
            try {
                passerelle.deconnecter(numero);
            } catch (ErreurMetierException serviceArrete) {
                // wa-web injoignable : rien a fermer
            }
        }
        em.createNativeQuery("""
                insert into crm_session_whatsapp (numero, fournisseur, meta_phone_number_id, statut, sante, raison, connecte_le, updated_at)
                values (:numero, 'META', :id, 'CONNECTE', 'OK', :raison, utc_timestamp(), utc_timestamp())
                on duplicate key update fournisseur = 'META', meta_phone_number_id = :id, statut = 'CONNECTE', sante = 'OK', raison = :raison,
                    connecte_le = utc_timestamp(), updated_at = utc_timestamp()
                """).setParameter("numero", numero).setParameter("id", phoneNumberId).setParameter("raison", verifie).executeUpdate();
        journalActionService.enregistrer("WHATSAPP_PASSAGE_META", "crm_utilisateur", agentId, numero + " -> API Meta " + phoneNumberId + " (" + verifie + ")");
        return session(agentId);
    }

    @Override
    @Transactional
    public SessionAgentDTO passerWhatsappWeb(Long agentId) {
        String numero = numeroAutorise(agentId);
        em.createNativeQuery("""
                update crm_session_whatsapp set fournisseur = 'WA_WEB', meta_phone_number_id = null, statut = 'DECONNECTE', raison = null,
                       updated_at = utc_timestamp()
                where numero = :numero
                """).setParameter("numero", numero).executeUpdate();
        journalActionService.enregistrer("WHATSAPP_PASSAGE_WA_WEB", "crm_utilisateur", agentId, numero + " -> WhatsApp Web");
        return session(agentId);
    }

    private SessionAgentDTO session(Long agentId) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " and u.id = :id", Tuple.class).setParameter("id", agentId).getResultList();
        return mapTupleToDto(lignes.getFirst());
    }

    /** Identifiant Meta du numero s'il passe par l'API officielle, sinon null (WhatsApp Web). */
    private String phoneNumberIdMeta(String numero) {
        @SuppressWarnings("unchecked")
        List<String> ids = em.createNativeQuery("select meta_phone_number_id from crm_session_whatsapp where numero = :numero and fournisseur = 'META'")
                .setParameter("numero", numero).getResultList();
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private EtatSessionDTO etatEnBase(String numero) {
        @SuppressWarnings("unchecked")
        List<Object[]> lignes = em.createNativeQuery("select statut, sante, raison from crm_session_whatsapp where numero = :numero")
                .setParameter("numero", numero).getResultList();
        Object[] l = lignes.getFirst();
        return EtatSessionDTO.builder().statut((String) l[0]).sante((String) l[1]).raison((String) l[2]).build();
    }

    private String numeroAutorise(Long agentId) {
        UtilisateurPrincipal utilisateur = utilisateurCourantService.utilisateur();
        if (!agentId.equals(utilisateur.getId()) && utilisateur.getAuthorities().stream().noneMatch(a -> GESTIONNAIRES.contains(a.getAuthority()))) {
            throw new AccessDeniedException("Seuls l'agent et la Direction du Recouvrement lient ce numéro");
        }
        @SuppressWarnings("unchecked")
        List<String> numeros = em.createNativeQuery("select numero_whatsapp from crm_utilisateur where id = :id and statut = 'ACTIF'")
                .setParameter("id", agentId).getResultList();
        if (numeros.isEmpty()) {
            throw new RessourceIntrouvableException("Agent introuvable");
        }
        if (numeros.getFirst() == null) {
            throw new ErreurMetierException("Enregistrez d'abord le numéro WhatsApp professionnel de l'agent.");
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
                .fournisseur(tuple.get("fournisseur", String.class))
                .metaPhoneNumberId(tuple.get("metaPhoneNumberId", String.class))
                .statut(tuple.get("statut", String.class))
                .sante(tuple.get("sante", String.class))
                .raison(tuple.get("raison", String.class))
                .connecteLe(tuple.get("connecteLe", LocalDateTime.class))
                .envoyesAujourdhui(((Number) tuple.get("envoyesAujourdhui")).longValue())
                .enAttente(((Number) tuple.get("enAttente")).longValue())
                .build();
    }
}
