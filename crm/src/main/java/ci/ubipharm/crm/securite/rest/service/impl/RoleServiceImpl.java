package ci.ubipharm.crm.securite.rest.service.impl;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.securite.rest.service.JournalActionService;
import ci.ubipharm.crm.securite.rest.service.RoleService;
import ci.ubipharm.crm.securite.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.securite.rest.service.dto.DroitDTO;
import ci.ubipharm.crm.securite.rest.service.dto.RoleDemandeDTO;
import ci.ubipharm.crm.securite.rest.service.dto.RoleGestionDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    static final String ADMIN = "ADMIN";
    /** Le role Administrateur garde toujours la main sur les comptes et les roles, pour ne jamais s'enfermer dehors. */
    static final Set<String> DROITS_ADMIN_VERROUILLES = Set.of("UTILISATEURS", "ROLES");

    private static final String SELECT = """
            select r.id as id, r.code as code, r.libelle as libelle, r.description as description, r.vision_globale as visionGlobale,
                   r.actif as actif, r.systeme as systeme, u.nom_complet as modifiePar, r.updated_at as modifieLe,
                   (select count(distinct h.utilisateur_id) from crm_habilitation h join crm_utilisateur c on c.id = h.utilisateur_id
                     where h.role_id = r.id and h.actif = true and c.statut = 'ACTIF') as nbUtilisateurs
            from crm_role r
            left join crm_utilisateur u on u.id = r.modifie_par
            """;

    private final EntityManager em;
    private final JournalActionService journalActionService;
    private final UtilisateurCourantService utilisateurCourantService;

    @Override
    @Transactional(readOnly = true)
    public List<RoleGestionDTO> lister() {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " order by r.actif desc, r.systeme desc, r.libelle", Tuple.class).getResultList();
        Map<Long, List<String>> droits = droitsParRole();
        return lignes.stream().map(t -> mapTupleToDto(t, droits)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RoleGestionDTO consulter(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where r.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Rôle introuvable");
        }
        return mapTupleToDto(lignes.getFirst(), droitsParRole());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DroitDTO> droits() {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select d.code as code, d.libelle as libelle, d.description as description, d.groupe as groupe,
                       m.code as module, m.libelle as moduleLibelle
                from crm_droit d left join crm_module m on m.id = d.module_id
                order by d.ordre
                """, Tuple.class).getResultList();
        return lignes.stream().map(this::mapTupleToDroit).toList();
    }

    @Override
    @Transactional
    public RoleGestionDTO creer(RoleDemandeDTO demande) {
        String libelle = demande.getLibelle().trim();
        verifierLibelle(libelle, 0L);
        Map<String, Long> droits = verifierDroits(demande.getDroits());
        em.createNativeQuery("""
                insert into crm_role (code, libelle, description, vision_globale, actif, systeme, modifie_par, created_at)
                values (:code, :libelle, :description, :visionGlobale, true, false, :par, utc_timestamp())
                """)
                .setParameter("code", codeLibre(libelle))
                .setParameter("libelle", libelle)
                .setParameter("description", vide(demande.getDescription()))
                .setParameter("visionGlobale", demande.isVisionGlobale())
                .setParameter("par", utilisateurCourantService.utilisateur().getId())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        droits.values().forEach(droitId -> ajouterDroit(id, droitId));
        journalActionService.enregistrer("ROLE_CREATION", "crm_role", id, libelle + " : " + String.join(", ", droits.keySet()));
        return consulter(id);
    }

    @Override
    @Transactional
    public RoleGestionDTO modifier(Long id, RoleDemandeDTO demande) {
        RoleGestionDTO role = verrouiller(id);
        String libelle = demande.getLibelle().trim();
        verifierLibelle(libelle, id);
        Map<String, Long> droits = verifierDroits(demande.getDroits());
        if (ADMIN.equals(role.getCode()) && !droits.keySet().containsAll(DROITS_ADMIN_VERROUILLES)) {
            throw new ErreurMetierException("Le rôle Administrateur garde toujours « Utilisateurs et habilitations » et « Rôles et droits ».");
        }
        Set<String> avant = new LinkedHashSet<>(role.getDroits());
        List<String> ajoutes = droits.keySet().stream().filter(d -> !avant.contains(d)).toList();
        List<String> retires = avant.stream().filter(d -> !droits.containsKey(d)).toList();
        boolean perimetreChange = role.isVisionGlobale() != demande.isVisionGlobale();

        em.createNativeQuery("""
                update crm_role set libelle = :libelle, description = :description, vision_globale = :visionGlobale,
                       modifie_par = :par, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("libelle", libelle)
                .setParameter("description", vide(demande.getDescription()))
                .setParameter("visionGlobale", demande.isVisionGlobale())
                .setParameter("par", utilisateurCourantService.utilisateur().getId())
                .setParameter("id", id)
                .executeUpdate();
        if (!retires.isEmpty()) {
            em.createNativeQuery("""
                    update crm_role_droit rd join crm_droit d on d.id = rd.droit_id set rd.actif = false, rd.updated_at = utc_timestamp()
                    where rd.role_id = :id and d.code in (:codes)
                    """)
                    .setParameter("id", id).setParameter("codes", retires).executeUpdate();
        }
        ajoutes.forEach(code -> ajouterDroit(id, droits.get(code)));

        List<String> details = new ArrayList<>();
        if (!libelle.equals(role.getLibelle())) {
            details.add("nom : " + role.getLibelle() + " -> " + libelle);
        }
        if (!ajoutes.isEmpty()) {
            details.add("ajoutés : " + String.join(", ", ajoutes));
        }
        if (!retires.isEmpty()) {
            details.add("retirés : " + String.join(", ", retires));
        }
        if (perimetreChange) {
            details.add(demande.isVisionGlobale() ? "toutes les agences" : "ses agences seulement");
        }
        if (!ajoutes.isEmpty() || !retires.isEmpty() || perimetreChange) {
            reviserDroitsDesTitulaires(id);
        }
        journalActionService.enregistrer("ROLE_MODIFICATION", "crm_role", id,
                libelle + (details.isEmpty() ? " : description" : " : " + String.join(" ; ", details)));
        return consulter(id);
    }

    @Override
    @Transactional
    public RoleGestionDTO desactiver(Long id) {
        RoleGestionDTO role = verrouiller(id);
        if (ADMIN.equals(role.getCode())) {
            throw new ErreurMetierException("Le rôle Administrateur reste toujours actif.");
        }
        if (role.getNbUtilisateurs() > 0) {
            throw new ErreurMetierException("Ce rôle est attribué à " + role.getNbUtilisateurs()
                    + (role.getNbUtilisateurs() > 1 ? " comptes actifs" : " compte actif") + " : retirez-le-leur d'abord.");
        }
        return changerActif(role, false, "ROLE_DESACTIVATION");
    }

    @Override
    @Transactional
    public RoleGestionDTO reactiver(Long id) {
        return changerActif(verrouiller(id), true, "ROLE_REACTIVATION");
    }

    private RoleGestionDTO changerActif(RoleGestionDTO role, boolean actif, String action) {
        if (role.isActif() == actif) {
            return role;
        }
        em.createNativeQuery("update crm_role set actif = :actif, modifie_par = :par, updated_at = utc_timestamp() where id = :id")
                .setParameter("actif", actif)
                .setParameter("par", utilisateurCourantService.utilisateur().getId())
                .setParameter("id", role.getId())
                .executeUpdate();
        // Un compte desactive peut encore porter ce role : sa session se ferme s'il en avait une
        reviserDroitsDesTitulaires(role.getId());
        journalActionService.enregistrer(action, "crm_role", role.getId(), role.getLibelle());
        return consulter(role.getId());
    }

    private void verifierLibelle(String libelle, Long id) {
        Number homonymes = (Number) em.createNativeQuery("select count(*) from crm_role where lower(libelle) = lower(:libelle) and id <> :id")
                .setParameter("libelle", libelle).setParameter("id", id).getSingleResult();
        if (homonymes.longValue() > 0) {
            throw new ErreurMetierException("Un rôle s'appelle déjà « " + libelle + " ».");
        }
    }

    /** Codes coches -> identifiants, dans l'ordre du catalogue ; un code inconnu est refuse. */
    private Map<String, Long> verifierDroits(List<String> codes) {
        Set<String> demandes = new LinkedHashSet<>(codes == null ? List.of() : codes.stream().filter(Objects::nonNull).toList());
        if (demandes.isEmpty()) {
            throw new ErreurMetierException("Cochez au moins un écran pour ce rôle.");
        }
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("select code as code, id as id from crm_droit order by ordre", Tuple.class).getResultList();
        Map<String, Long> connus = new LinkedHashMap<>();
        lignes.forEach(t -> connus.put(t.get("code", String.class), t.get("id", Long.class)));
        List<String> inconnus = demandes.stream().filter(c -> !connus.containsKey(c)).toList();
        if (!inconnus.isEmpty()) {
            throw new ErreurMetierException("Droit inconnu : " + String.join(", ", inconnus) + ".");
        }
        Map<String, Long> retenus = new LinkedHashMap<>();
        connus.forEach((code, id) -> {
            if (demandes.contains(code)) {
                retenus.put(code, id);
            }
        });
        return retenus;
    }

    private void ajouterDroit(Long roleId, Long droitId) {
        em.createNativeQuery("""
                insert into crm_role_droit (role_id, droit_id, actif, created_at) values (:role, :droit, true, utc_timestamp())
                on duplicate key update actif = true, updated_at = utc_timestamp()
                """)
                .setParameter("role", roleId).setParameter("droit", droitId).executeUpdate();
    }

    /** Les comptes qui ont ce role se reconnectent pour prendre ses nouveaux droits (voir ControleSessionFiltre). */
    private void reviserDroitsDesTitulaires(Long roleId) {
        em.createNativeQuery("""
                update crm_utilisateur u set u.droits_revises_le = utc_timestamp(6)
                where exists (select 1 from crm_habilitation h where h.utilisateur_id = u.id and h.role_id = :id and h.actif = true)
                """).setParameter("id", roleId).executeUpdate();
    }

    /** Code technique tire du nom : « Administrateur sans SMS » -> ADMINISTRATEUR_SANS_SMS. */
    private String codeLibre(String libelle) {
        String base = Normalizer.normalize(libelle, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "ROLE";
        }
        base = base.length() > 40 ? base.substring(0, 40) : base;
        String code = base;
        for (int i = 2; ((Number) em.createNativeQuery("select count(*) from crm_role where code = :code")
                .setParameter("code", code).getSingleResult()).longValue() > 0; i++) {
            code = base + "_" + i;
        }
        return code;
    }

    private RoleGestionDTO verrouiller(Long id) {
        @SuppressWarnings("unchecked")
        List<Number> lignes = em.createNativeQuery("select id from crm_role where id = :id for update").setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Rôle introuvable");
        }
        return consulter(id);
    }

    private Map<Long, List<String>> droitsParRole() {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select rd.role_id as roleId, d.code as code from crm_role_droit rd join crm_droit d on d.id = rd.droit_id
                where rd.actif = true order by d.ordre
                """, Tuple.class).getResultList();
        Map<Long, List<String>> droits = new LinkedHashMap<>();
        lignes.forEach(t -> droits.computeIfAbsent(t.get("roleId", Long.class), k -> new ArrayList<>()).add(t.get("code", String.class)));
        return droits;
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static boolean vrai(Object valeur) {
        return valeur instanceof Boolean b ? b : valeur instanceof Number n && n.intValue() != 0;
    }

    RoleGestionDTO mapTupleToDto(Tuple tuple, Map<Long, List<String>> droits) {
        Long id = tuple.get("id", Long.class);
        return RoleGestionDTO.builder()
                .id(id)
                .code(tuple.get("code", String.class))
                .libelle(tuple.get("libelle", String.class))
                .description(tuple.get("description", String.class))
                .visionGlobale(vrai(tuple.get("visionGlobale")))
                .actif(vrai(tuple.get("actif")))
                .systeme(vrai(tuple.get("systeme")))
                .nbUtilisateurs(((Number) tuple.get("nbUtilisateurs")).longValue())
                .droits(droits.getOrDefault(id, List.of()))
                .modifiePar(tuple.get("modifiePar", String.class))
                .modifieLe(tuple.get("modifieLe", LocalDateTime.class))
                .build();
    }

    DroitDTO mapTupleToDroit(Tuple tuple) {
        return DroitDTO.builder()
                .code(tuple.get("code", String.class))
                .libelle(tuple.get("libelle", String.class))
                .description(tuple.get("description", String.class))
                .groupe(tuple.get("groupe", String.class))
                .module(tuple.get("module", String.class))
                .moduleLibelle(tuple.get("moduleLibelle", String.class))
                .build();
    }
}
