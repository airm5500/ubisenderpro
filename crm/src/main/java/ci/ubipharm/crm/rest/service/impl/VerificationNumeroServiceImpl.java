package ci.ubipharm.crm.rest.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.VerificationNumeroService;
import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.rest.service.dto.VerificationNumeroDTO;
import ci.ubipharm.crm.rest.service.dto.VerificationNumerosDTO;
import ci.ubipharm.crm.rest.service.dto.VerificationNumerosDemandeDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VerificationNumeroServiceImpl implements VerificationNumeroService {

    private static final int LOT = 50;

    private final EntityManager em;
    private final PasserelleWhatsappService passerelle;
    private final ParametreService parametreService;
    private final PerimetreService perimetreService;
    private final UtilisateurCourantService utilisateurCourantService;

    @Override
    @Transactional
    public VerificationNumerosDTO verifier(VerificationNumerosDemandeDTO demande) {
        String prefixe = parametreService.valeur("whatsapp.prefixe_pays", "225");
        List<VerificationNumeroDTO> resultats = new ArrayList<>();
        if (demande.getNumeros() != null) {
            new LinkedHashSet<>(demande.getNumeros()).stream().filter(n -> n != null && !n.isBlank())
                    .forEach(n -> resultats.add(VerificationNumeroDTO.builder().saisi(n.trim()).build()));
        }
        if (demande.getClientIds() != null && !demande.getClientIds().isEmpty()) {
            resultats.addAll(numerosDesClients(demande.getClientIds()));
        }
        if (resultats.isEmpty()) {
            throw new ErreurMetierException("Saisissez des numéros ou cochez des clients à vérifier.");
        }
        if (resultats.size() > 1000) {
            throw new ErreurMetierException("1000 numéros au plus par vérification.");
        }
        resultats.forEach(r -> {
            r.setNumero(ValeursImport.numeroWhatsapp(r.getSaisi(), prefixe).orElse(null));
            r.setResultat(r.getNumero() == null ? "FORMAT_INVALIDE" : "NON_VERIFIE");
        });

        String session = session();
        String avertissement = null;
        List<String> aInterroger = resultats.stream().map(VerificationNumeroDTO::getNumero).filter(n -> n != null).distinct().toList();
        if (session == null) {
            avertissement = "Aucun numéro WhatsApp Web n'est relié : seul le format a été contrôlé. Reliez un téléphone dans Numéros WhatsApp.";
        } else {
            try {
                for (int debut = 0; debut < aInterroger.size(); debut += LOT) {
                    List<String> lot = aInterroger.subList(debut, Math.min(debut + LOT, aInterroger.size()));
                    Map<String, Boolean> presence = passerelle.verifierNumeros(session, lot);
                    for (VerificationNumeroDTO r : resultats) {
                        if (r.getNumero() != null && presence.containsKey(r.getNumero())) {
                            r.setResultat(presence.get(r.getNumero()) ? "SUR_WHATSAPP" : "ABSENT");
                        }
                    }
                    presence.forEach((numero, present) -> em.createNativeQuery("""
                            update crm_contact set whatsapp_present = :present, whatsapp_verifie_le = utc_timestamp()
                            where numero_whatsapp = :numero and statut = 'ACTIF'
                            """).setParameter("present", present).setParameter("numero", numero).executeUpdate());
                }
            } catch (ErreurMetierException e) {
                avertissement = e.getMessage() + " Les numéros restants n'ont pas été vérifiés.";
            }
        }
        return VerificationNumerosDTO.builder()
                .sessionUtilisee(session)
                .avertissement(avertissement)
                .surWhatsapp(compter(resultats, "SUR_WHATSAPP"))
                .absents(compter(resultats, "ABSENT"))
                .formatsInvalides(compter(resultats, "FORMAT_INVALIDE"))
                .nonVerifies(compter(resultats, "NON_VERIFIE"))
                .resultats(resultats)
                .build();
    }

    /** Le numero de l'utilisateur s'il est relie par WhatsApp Web, sinon le premier numero relie. */
    private String session() {
        @SuppressWarnings("unchecked")
        List<String> numeros = em.createNativeQuery("""
                select s.numero from crm_session_whatsapp s
                where s.statut = 'CONNECTE' and coalesce(s.fournisseur, 'WA_WEB') = 'WA_WEB'
                order by case when s.numero = (select u.numero_whatsapp from crm_utilisateur u where u.id = :moi) then 0 else 1 end, s.id
                """).setParameter("moi", utilisateurCourantService.utilisateur().getId()).setMaxResults(1).getResultList();
        return numeros.isEmpty() ? null : numeros.getFirst();
    }

    private List<VerificationNumeroDTO> numerosDesClients(List<Long> clientIds) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        Query requete = em.createNativeQuery("""
                select c.id as clientId, c.raison_sociale as client, ct.fonction as fonction, ct.numero_whatsapp as numero
                from crm_client c join crm_contact ct on ct.client_id = c.id and ct.statut = 'ACTIF' and ct.numero_whatsapp is not null
                where c.id in (:ids)
                """ + (perimetre.isVisionGlobale() ? "" : " and c.agence_id in (:agences)") + " order by c.raison_sociale, ct.rang, ct.id", Tuple.class)
                .setParameter("ids", clientIds);
        if (!perimetre.isVisionGlobale()) {
            requete.setParameter("agences", perimetre.getAgenceIds().isEmpty() ? List.of(0L) : perimetre.getAgenceIds());
        }
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return lignes.stream().map(t -> VerificationNumeroDTO.builder()
                .clientId(((Number) t.get("clientId")).longValue())
                .client(t.get("client", String.class))
                .fonction(t.get("fonction", String.class))
                .saisi(t.get("numero", String.class))
                .build()).toList();
    }

    private static int compter(List<VerificationNumeroDTO> resultats, String resultat) {
        return (int) resultats.stream().filter(r -> resultat.equals(r.getResultat())).count();
    }
}
