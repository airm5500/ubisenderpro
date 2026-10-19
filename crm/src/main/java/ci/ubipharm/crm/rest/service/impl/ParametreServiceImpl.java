package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.dto.ParametreDTO;
import ci.ubipharm.crm.rest.service.dto.ParametreModificationDTO;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParametreServiceImpl implements ParametreService {

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern URL = Pattern.compile("https://[^\\s]+");
    private static final Pattern COULEUR = Pattern.compile("#[0-9A-Fa-f]{6}");
    private static final Pattern ENTIER = Pattern.compile("-?\\d{1,9}");

    /** Couples dont la premiere valeur ne peut depasser la seconde, avec le message d'erreur. */
    private static final List<String[]> ORDRES = List.of(
            new String[] {"whatsapp.envoi.heure_debut", "whatsapp.envoi.heure_fin", "WhatsApp : l'heure de fin d'envoi doit suivre l'heure de début."},
            new String[] {"email.envoi.heure_debut", "email.envoi.heure_fin", "E-mail : l'heure de fin d'envoi doit suivre l'heure de début."},
            new String[] {"sms.envoi.heure_debut", "sms.envoi.heure_fin", "SMS : l'heure de fin d'envoi doit suivre l'heure de début."},
            new String[] {"whatsapp.envoi.delai_min_secondes", "whatsapp.envoi.delai_max_secondes",
                "WhatsApp : l'attente minimale entre deux messages dépasse l'attente maximale."},
            new String[] {"campagne.avant_echeance_jours_min", "campagne.avant_echeance_jours_max",
                "Relance avant échéance : le dernier jour (J-n) doit être plus proche de l'échéance que le premier."});

    private static final String SELECT = """
            select p.cle as cle, p.valeur as valeur, p.type_valeur as typeValeur, p.minimum as minimum, p.maximum as maximum,
                   p.obligatoire as obligatoire, p.description as description, p.categorie as categorie, u.nom_complet as modifiePar,
                   p.updated_at as modifieLe
            from crm_parametre p
            left join crm_utilisateur u on u.id = p.modifie_par
            where p.modifiable = true
            """;

    private final EntityManager em;
    private final JournalActionService journalActionService;
    private final UtilisateurCourantService utilisateurCourantService;

    @Override
    @Transactional(readOnly = true)
    public String valeur(String cle, String parDefaut) {
        @SuppressWarnings("unchecked")
        List<String> valeurs = em.createNativeQuery("select valeur from crm_parametre where cle = :cle")
                .setParameter("cle", cle)
                .getResultList();
        return valeurs.isEmpty() || valeurs.getFirst() == null || valeurs.getFirst().isBlank() ? parDefaut : valeurs.getFirst().trim();
    }

    @Override
    public int entier(String cle, int parDefaut) {
        try {
            return Integer.parseInt(valeur(cle, String.valueOf(parDefaut)));
        } catch (NumberFormatException e) {
            return parDefaut;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParametreDTO> lister() {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " order by p.categorie, p.ordre, p.cle", Tuple.class).getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
    }

    @Override
    @Transactional
    public List<ParametreDTO> modifier(List<ParametreModificationDTO> modifications) {
        if (modifications == null || modifications.isEmpty()) {
            return lister();
        }
        Map<String, ParametreDTO> existants = new LinkedHashMap<>();
        lister().forEach(p -> existants.put(p.getCle(), p));
        Map<String, String> nouvelles = new LinkedHashMap<>();
        Set<String> vues = new HashSet<>();
        for (ParametreModificationDTO m : modifications) {
            ParametreDTO p = m == null || m.getCle() == null ? null : existants.get(m.getCle());
            if (p == null || !vues.add(p.getCle())) {
                throw new ErreurMetierException("Paramètre inconnu ou en double : " + (m == null ? "" : m.getCle()) + ".");
            }
            nouvelles.put(p.getCle(), verifier(p, m.getValeur()));
        }
        Map<String, String> apres = new HashMap<>();
        existants.forEach((cle, p) -> apres.put(cle, p.getValeur()));
        apres.putAll(nouvelles);
        for (String[] ordre : ORDRES) {
            Integer premier = nombre(apres.get(ordre[0]));
            Integer second = nombre(apres.get(ordre[1]));
            boolean heures = ordre[0].endsWith("heure_debut");
            if (premier != null && second != null && (heures ? premier >= second : premier > second)) {
                throw new ErreurMetierException(ordre[2]);
            }
        }

        Long par = utilisateurCourantService.utilisateur().getId();
        nouvelles.forEach((cle, valeur) -> {
            String ancienne = existants.get(cle).getValeur();
            if (Objects.equals(vide(ancienne), vide(valeur))) {
                return;
            }
            em.createNativeQuery("update crm_parametre set valeur = :valeur, modifie_par = :par, updated_at = utc_timestamp() where cle = :cle")
                    .setParameter("valeur", valeur)
                    .setParameter("par", par)
                    .setParameter("cle", cle)
                    .executeUpdate();
            journalActionService.enregistrer("PARAMETRE_MODIFICATION", "crm_parametre", null,
                    cle + " : " + (ancienne == null || ancienne.isBlank() ? "(vide)" : ancienne) + " -> " + (valeur == null ? "(vide)" : valeur));
        });
        return lister();
    }

    /** Valeur nettoyee et controlee selon le type du parametre ; null = vide. */
    private static String verifier(ParametreDTO p, String saisie) {
        String valeur = vide(saisie);
        if (valeur == null) {
            if (p.isObligatoire()) {
                throw new ErreurMetierException("« " + p.getDescription() + " » ne peut pas rester vide.");
            }
            return null;
        }
        switch (p.getTypeValeur()) {
            case "ENTIER", "HEURE" -> {
                Integer n = nombre(valeur);
                if (n == null || p.getMinimum() != null && n < p.getMinimum() || p.getMaximum() != null && n > p.getMaximum()) {
                    throw new ErreurMetierException("« " + p.getDescription() + " » : saisissez un nombre entier"
                            + (p.getMinimum() != null && p.getMaximum() != null ? " entre " + p.getMinimum() + " et " + p.getMaximum() : "") + ".");
                }
                return String.valueOf(n);
            }
            case "EMAIL" -> {
                if (!EMAIL.matcher(valeur).matches()) {
                    throw new ErreurMetierException("« " + p.getDescription() + " » : adresse e-mail invalide.");
                }
                return valeur.toLowerCase(java.util.Locale.ROOT);
            }
            case "URL" -> {
                if (!URL.matcher(valeur).matches()) {
                    throw new ErreurMetierException("« " + p.getDescription() + " » : l'adresse commence par https://.");
                }
                return valeur.replaceAll("/+$", "");
            }
            case "COULEUR" -> {
                if (!COULEUR.matcher(valeur).matches()) {
                    throw new ErreurMetierException("« " + p.getDescription() + " » : couleur au format #05253D.");
                }
                return valeur.toUpperCase(java.util.Locale.ROOT);
            }
            default -> {
                return valeur;
            }
        }
    }

    private static Integer nombre(String valeur) {
        return valeur != null && ENTIER.matcher(valeur.trim()).matches() ? Integer.valueOf(valeur.trim()) : null;
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static boolean vrai(Object valeur) {
        return valeur instanceof Boolean b ? b : valeur instanceof Number n && n.intValue() != 0;
    }

    ParametreDTO mapTupleToDto(Tuple tuple) {
        return ParametreDTO.builder()
                .cle(tuple.get("cle", String.class))
                .valeur(tuple.get("valeur", String.class))
                .typeValeur(tuple.get("typeValeur", String.class))
                .minimum(tuple.get("minimum") == null ? null : ((Number) tuple.get("minimum")).intValue())
                .maximum(tuple.get("maximum") == null ? null : ((Number) tuple.get("maximum")).intValue())
                .obligatoire(vrai(tuple.get("obligatoire")))
                .description(tuple.get("description", String.class))
                .categorie(tuple.get("categorie", String.class))
                .modifiePar(tuple.get("modifiePar", String.class))
                .modifieLe(tuple.get("modifieLe", LocalDateTime.class))
                .build();
    }
}
