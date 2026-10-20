package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.AideService;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.AideDTO;
import ci.ubipharm.crm.rest.service.dto.AideTexteCleDTO;
import ci.ubipharm.crm.rest.service.dto.AideTexteDTO;
import ci.ubipharm.crm.rest.service.dto.AideTexteDemandeDTO;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AideServiceImpl implements AideService {

    static final String PARAMETRE_GUIDAGE = "aide.guidage";

    private static final String SELECT = """
            select a.type_aide as type, a.cle as cle, a.titre as titre, a.texte as texte,
                   u.nom_complet as modifiePar, coalesce(a.updated_at, a.created_at) as modifieLe
            from crm_aide_texte a
            left join crm_utilisateur u on u.id = a.modifie_par
            """;

    private final EntityManager em;
    private final ParametreService parametreService;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;

    @Override
    @Transactional(readOnly = true)
    public AideDTO aide() {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " order by a.type_aide, a.cle", Tuple.class).getResultList();
        Object actif = em.createNativeQuery("select guidage_actif from crm_utilisateur where id = :id")
                .setParameter("id", utilisateurCourantService.utilisateur().getId()).getSingleResult();
        return AideDTO.builder()
                .guidageEntreprise(parametreService.oui(PARAMETRE_GUIDAGE, true))
                .guidageUtilisateur(vrai(actif))
                .textes(lignes.stream().map(this::mapTupleToDto).toList())
                .build();
    }

    @Override
    @Transactional
    public AideDTO preference(boolean actif) {
        em.createNativeQuery("update crm_utilisateur set guidage_actif = :actif where id = :id")
                .setParameter("actif", actif).setParameter("id", utilisateurCourantService.utilisateur().getId())
                .executeUpdate();
        return aide();
    }

    @Override
    @Transactional
    public AideTexteDTO modifier(AideTexteDemandeDTO demande) {
        String cle = demande.getCle().trim();
        em.createNativeQuery("""
                insert into crm_aide_texte (type_aide, cle, titre, texte, modifie_par, created_at)
                values (:type, :cle, :titre, :texte, :par, utc_timestamp())
                on duplicate key update titre = values(titre), texte = values(texte), modifie_par = values(modifie_par), updated_at = utc_timestamp()
                """)
                .setParameter("type", demande.getType()).setParameter("cle", cle)
                .setParameter("titre", demande.getTitre().trim()).setParameter("texte", demande.getTexte().trim())
                .setParameter("par", utilisateurCourantService.utilisateur().getId())
                .executeUpdate();
        AideTexteDTO texte = consulter(demande.getType(), cle);
        journalActionService.enregistrer("PARAMETRE_AIDE_MODIFICATION", "crm_aide_texte", null, libelle(demande.getType(), cle));
        return texte;
    }

    @Override
    @Transactional
    public void reinitialiser(AideTexteCleDTO cle) {
        int supprimes = em.createNativeQuery("delete from crm_aide_texte where type_aide = :type and cle = :cle")
                .setParameter("type", cle.getType()).setParameter("cle", cle.getCle().trim()).executeUpdate();
        if (supprimes > 0) {
            journalActionService.enregistrer("PARAMETRE_AIDE_MODIFICATION", "crm_aide_texte", null,
                    libelle(cle.getType(), cle.getCle().trim()) + " : texte d'origine rétabli");
        }
    }

    private static String libelle(String type, String cle) {
        return ("PAGE".equals(type) ? "Aide de l'écran " : "Infobulle ") + cle;
    }

    private AideTexteDTO consulter(String type, String cle) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where a.type_aide = :type and a.cle = :cle", Tuple.class)
                .setParameter("type", type).setParameter("cle", cle).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Texte d'aide introuvable");
        }
        return mapTupleToDto(lignes.getFirst());
    }

    /** MariaDB rend un BOOLEAN en Boolean, Byte ou Integer selon le pilote. */
    static boolean vrai(Object valeur) {
        if (valeur instanceof Boolean b) {
            return b;
        }
        return valeur instanceof Number n && n.intValue() != 0;
    }

    AideTexteDTO mapTupleToDto(Tuple tuple) {
        return AideTexteDTO.builder()
                .type(tuple.get("type", String.class))
                .cle(tuple.get("cle", String.class))
                .titre(tuple.get("titre", String.class))
                .texte(tuple.get("texte", String.class))
                .modifiePar(tuple.get("modifiePar", String.class))
                .modifieLe(tuple.get("modifieLe", LocalDateTime.class))
                .build();
    }
}
