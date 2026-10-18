package ci.ubipharm.crm.securite.rest.service.impl;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.commun.rest.service.ParametreService;
import ci.ubipharm.crm.commun.rest.service.impl.ValeursImport;
import ci.ubipharm.crm.securite.rest.service.JournalActionService;
import ci.ubipharm.crm.securite.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.securite.rest.service.UtilisateurService;
import ci.ubipharm.crm.securite.rest.service.dto.AgenceUtilisateurDTO;
import ci.ubipharm.crm.securite.rest.service.dto.ChangementMotDePasseDTO;
import ci.ubipharm.crm.securite.rest.service.dto.CompteProvisoireDTO;
import ci.ubipharm.crm.securite.rest.service.dto.HabilitationDTO;
import ci.ubipharm.crm.securite.rest.service.dto.ModuleDTO;
import ci.ubipharm.crm.securite.rest.service.dto.ReferentielHabilitationDTO;
import ci.ubipharm.crm.securite.rest.service.dto.RoleDTO;
import ci.ubipharm.crm.securite.rest.service.dto.UtilisateurCriteresDTO;
import ci.ubipharm.crm.securite.rest.service.dto.UtilisateurDTO;
import ci.ubipharm.crm.securite.rest.service.dto.UtilisateurDemandeDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

/**
 * Comptes geres par l'administrateur. Un compte n'est jamais supprime : il est desactive, ses habilitations
 * retirees passent a inactif et ses rattachements d'agence sont clotures, pour garder l'historique.
 */
@Service
@RequiredArgsConstructor
public class UtilisateurServiceImpl implements UtilisateurService {

    private static final String SELECT = """
            select u.id as id, u.login as login, u.nom_complet as nomComplet, u.email as email, u.numero_whatsapp as numeroWhatsapp,
                   u.statut as statut, u.mot_de_passe_provisoire as motDePasseProvisoire, u.echecs_connexion as echecsConnexion,
                   u.derniere_connexion as derniereConnexion, u.created_at as createdAt
            from crm_utilisateur u
            """;

    // Sans caracteres ambigus (0/O, 1/l/I) : le mot de passe provisoire est souvent dicte ou recopie
    private static final String LETTRES = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String CHIFFRES = "23456789";
    private static final int LONGUEUR_PROVISOIRE = 12;

    private final SecureRandom aleatoire = new SecureRandom();

    private final EntityManager em;
    private final PasswordEncoder passwordEncoder;
    private final ParametreService parametreService;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;

    @Override
    @Transactional(readOnly = true)
    public PageDTO<UtilisateurDTO> rechercher(UtilisateurCriteresDTO criteres) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> parametres = new LinkedHashMap<>();
        if (criteres.getStatut() != null && !criteres.getStatut().isBlank()) {
            where.append(" and u.statut = :statut");
            parametres.put("statut", criteres.getStatut());
        } else {
            where.append(" and u.statut <> 'ARCHIVE'");
        }
        if (criteres.getQ() != null && !criteres.getQ().isBlank()) {
            where.append(" and (u.login like :q or u.nom_complet like :q or u.email like :q or u.numero_whatsapp like :q)");
            parametres.put("q", "%" + echapperLike(criteres.getQ().trim()) + "%");
        }
        if (criteres.getRoleCode() != null && !criteres.getRoleCode().isBlank()) {
            where.append(" and exists (select 1 from crm_habilitation h join crm_role r on r.id = h.role_id"
                    + " where h.utilisateur_id = u.id and h.actif = true and r.code = :roleCode)");
            parametres.put("roleCode", criteres.getRoleCode().trim());
        }
        if (criteres.getAgenceId() != null) {
            where.append(" and exists (select 1 from crm_utilisateur_agence ua where ua.utilisateur_id = u.id and ua.agence_id = :agenceId"
                    + " and ua.date_debut <= utc_date() and (ua.date_fin is null or ua.date_fin >= utc_date()))");
            parametres.put("agenceId", criteres.getAgenceId());
        }

        Query comptage = em.createNativeQuery("select count(*) from crm_utilisateur u" + where);
        parametres.forEach(comptage::setParameter);
        long total = ((Number) comptage.getSingleResult()).longValue();

        Query requete = em.createNativeQuery(SELECT + where + " order by u.nom_complet, u.id", Tuple.class);
        parametres.forEach(requete::setParameter);
        requete.setFirstResult(criteres.getPage() * criteres.getTaille());
        requete.setMaxResults(criteres.getTaille());
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        List<UtilisateurDTO> utilisateurs = lignes.stream().map(this::mapTupleToDto).toList();
        completer(utilisateurs);

        return PageDTO.<UtilisateurDTO>builder()
                .elements(utilisateurs)
                .total(total)
                .page(criteres.getPage())
                .taille(criteres.getTaille())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UtilisateurDTO consulter(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where u.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Utilisateur introuvable");
        }
        UtilisateurDTO utilisateur = mapTupleToDto(lignes.getFirst());
        completer(List.of(utilisateur));
        return utilisateur;
    }

    @Override
    @Transactional(readOnly = true)
    public ReferentielHabilitationDTO referentiel() {
        @SuppressWarnings("unchecked")
        List<Tuple> roles = em.createNativeQuery("""
                select code as code, libelle as libelle, description as description, vision_globale as visionGlobale
                from crm_role where actif = true order by id
                """, Tuple.class).getResultList();
        @SuppressWarnings("unchecked")
        List<Tuple> modules = em.createNativeQuery("select code as code, libelle as libelle from crm_module where actif = true order by id", Tuple.class)
                .getResultList();
        return ReferentielHabilitationDTO.builder()
                .roles(roles.stream().map(r -> RoleDTO.builder()
                        .code(r.get("code", String.class))
                        .libelle(r.get("libelle", String.class))
                        .description(r.get("description", String.class))
                        .visionGlobale(vrai(r.get("visionGlobale")))
                        .build()).toList())
                .modules(modules.stream().map(m -> ModuleDTO.builder()
                        .code(m.get("code", String.class))
                        .libelle(m.get("libelle", String.class))
                        .build()).toList())
                .build();
    }

    @Override
    @Transactional
    public CompteProvisoireDTO creer(UtilisateurDemandeDTO demande) {
        if (demande.getLogin() == null || demande.getLogin().isBlank()) {
            throw new ErreurMetierException("Saisissez l'identifiant de connexion.");
        }
        String login = demande.getLogin().trim().toLowerCase(Locale.ROOT);
        Number existant = (Number) em.createNativeQuery("select count(*) from crm_utilisateur where login = :login")
                .setParameter("login", login).getSingleResult();
        if (existant.longValue() > 0) {
            throw new ErreurMetierException("L'identifiant « " + login + " » est déjà utilisé.");
        }
        String numero = numero(demande.getNumeroWhatsapp(), null);
        List<HabilitationVoulue> habilitations = habilitations(demande.getHabilitations());
        List<Long> agences = agences(demande.getAgenceIds());

        String motDePasse = motDePasseProvisoire();
        em.createNativeQuery("""
                insert into crm_utilisateur (login, nom_complet, email, numero_whatsapp, mot_de_passe_hash, mot_de_passe_provisoire, statut, created_at)
                values (:login, :nom, :email, :numero, :hash, true, 'ACTIF', utc_timestamp())
                """)
                .setParameter("login", login)
                .setParameter("nom", demande.getNomComplet().trim())
                .setParameter("email", vide(demande.getEmail()))
                .setParameter("numero", numero)
                .setParameter("hash", passwordEncoder.encode(motDePasse))
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        appliquerHabilitations(id, habilitations);
        appliquerAgences(id, agences);
        journalActionService.enregistrer("UTILISATEUR_CREATION", "crm_utilisateur", id, login + " : " + resume(habilitations));
        return CompteProvisoireDTO.builder().utilisateur(consulter(id)).motDePasseProvisoire(motDePasse).build();
    }

    @Override
    @Transactional
    public UtilisateurDTO modifier(Long id, UtilisateurDemandeDTO demande) {
        UtilisateurDTO avant = verrouiller(id);
        String numero = numero(demande.getNumeroWhatsapp(), id);
        List<HabilitationVoulue> habilitations = habilitations(demande.getHabilitations());
        List<Long> agences = agences(demande.getAgenceIds());
        boolean resteAdmin = habilitations.stream().anyMatch(h -> "ADMIN".equals(h.roleCode()));
        if (!resteAdmin && estAdmin(avant)) {
            if (id.equals(utilisateurCourantService.utilisateur().getId())) {
                throw new ErreurMetierException("Vous ne pouvez pas retirer votre propre rôle d'administrateur.");
            }
            verifierAutreAdmin(id);
        }

        em.createNativeQuery("""
                update crm_utilisateur set nom_complet = :nom, email = :email, numero_whatsapp = :numero, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("nom", demande.getNomComplet().trim())
                .setParameter("email", vide(demande.getEmail()))
                .setParameter("numero", numero)
                .setParameter("id", id)
                .executeUpdate();
        boolean droitsChanges = appliquerHabilitations(id, habilitations) | appliquerAgences(id, agences);
        if (droitsChanges) {
            reviserDroits(id);
        }
        journalActionService.enregistrer("UTILISATEUR_MODIFICATION", "crm_utilisateur", id,
                avant.getLogin() + " : " + resume(habilitations) + (droitsChanges ? " (droits modifiés)" : ""));
        return consulter(id);
    }

    @Override
    @Transactional
    public UtilisateurDTO desactiver(Long id) {
        UtilisateurDTO utilisateur = verrouiller(id);
        if (id.equals(utilisateurCourantService.utilisateur().getId())) {
            throw new ErreurMetierException("Vous ne pouvez pas désactiver votre propre compte.");
        }
        if (!"ACTIF".equals(utilisateur.getStatut())) {
            throw new ErreurMetierException("Ce compte est déjà désactivé.");
        }
        if (estAdmin(utilisateur)) {
            verifierAutreAdmin(id);
        }
        em.createNativeQuery("update crm_utilisateur set statut = 'DESACTIVE', updated_at = utc_timestamp() where id = :id")
                .setParameter("id", id).executeUpdate();
        reviserDroits(id);
        journalActionService.enregistrer("UTILISATEUR_DESACTIVATION", "crm_utilisateur", id, utilisateur.getLogin());
        return consulter(id);
    }

    @Override
    @Transactional
    public UtilisateurDTO reactiver(Long id) {
        UtilisateurDTO utilisateur = verrouiller(id);
        if ("ACTIF".equals(utilisateur.getStatut())) {
            throw new ErreurMetierException("Ce compte est déjà actif.");
        }
        em.createNativeQuery("update crm_utilisateur set statut = 'ACTIF', echecs_connexion = 0, updated_at = utc_timestamp() where id = :id")
                .setParameter("id", id).executeUpdate();
        journalActionService.enregistrer("UTILISATEUR_REACTIVATION", "crm_utilisateur", id, utilisateur.getLogin());
        return consulter(id);
    }

    @Override
    @Transactional
    public CompteProvisoireDTO reinitialiserMotDePasse(Long id) {
        UtilisateurDTO utilisateur = verrouiller(id);
        String motDePasse = motDePasseProvisoire();
        // Un compte bloque apres trop d'echecs est debloque : c'est le cas le plus courant de reinitialisation
        em.createNativeQuery("""
                update crm_utilisateur set mot_de_passe_hash = :hash, mot_de_passe_provisoire = true, echecs_connexion = 0,
                       statut = case when statut = 'DESACTIVE' then 'ACTIF' else statut end, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("hash", passwordEncoder.encode(motDePasse))
                .setParameter("id", id)
                .executeUpdate();
        reviserDroits(id);
        journalActionService.enregistrer("UTILISATEUR_REINITIALISATION_MDP", "crm_utilisateur", id, utilisateur.getLogin());
        return CompteProvisoireDTO.builder().utilisateur(consulter(id)).motDePasseProvisoire(motDePasse).build();
    }

    @Override
    @Transactional
    public void changerMonMotDePasse(ChangementMotDePasseDTO changement) {
        Long id = utilisateurCourantService.utilisateur().getId();
        Tuple compte = (Tuple) em.createNativeQuery("select login as login, mot_de_passe_hash as hash from crm_utilisateur where id = :id for update",
                Tuple.class).setParameter("id", id).getSingleResult();
        if (!passwordEncoder.matches(changement.getActuel(), compte.get("hash", String.class))) {
            throw new ErreurMetierException("Le mot de passe actuel est incorrect.");
        }
        String nouveau = changement.getNouveau();
        int longueurMin = parametreService.entier("securite.mot_de_passe_longueur_min", 10);
        if (nouveau.length() < longueurMin) {
            throw new ErreurMetierException("Le nouveau mot de passe doit contenir au moins " + longueurMin + " caractères.");
        }
        if (!nouveau.matches(".*\\p{L}.*") || !nouveau.matches(".*\\d.*")) {
            throw new ErreurMetierException("Le nouveau mot de passe doit contenir des lettres et des chiffres.");
        }
        if (nouveau.toLowerCase(Locale.ROOT).contains(compte.get("login", String.class).toLowerCase(Locale.ROOT))) {
            throw new ErreurMetierException("Le nouveau mot de passe ne doit pas contenir votre identifiant.");
        }
        if (nouveau.equals(changement.getActuel())) {
            throw new ErreurMetierException("Le nouveau mot de passe doit être différent de l'actuel.");
        }
        em.createNativeQuery("""
                update crm_utilisateur set mot_de_passe_hash = :hash, mot_de_passe_provisoire = false, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("hash", passwordEncoder.encode(nouveau))
                .setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("MOT_DE_PASSE_CHANGEMENT", "crm_utilisateur", id, null);
    }

    /** Role et module valides, sans doublon ; un role sur « tous les modules » couvre les autres lignes du meme role. */
    private List<HabilitationVoulue> habilitations(List<HabilitationDTO> demandees) {
        Map<String, Long> roles = codes("select code, id from crm_role where actif = true");
        Map<String, Long> modules = codes("select code, id from crm_module where actif = true");
        Set<HabilitationVoulue> voulues = new LinkedHashSet<>();
        for (HabilitationDTO h : demandees) {
            String roleCode = h.getRoleCode().trim();
            Long roleId = roles.get(roleCode);
            if (roleId == null) {
                throw new ErreurMetierException("Rôle inconnu : " + roleCode + ".");
            }
            String moduleCode = vide(h.getModuleCode());
            Long moduleId = null;
            if (moduleCode != null) {
                moduleId = modules.get(moduleCode);
                if (moduleId == null) {
                    throw new ErreurMetierException("Module inconnu : " + moduleCode + ".");
                }
            }
            voulues.add(new HabilitationVoulue(roleId, roleCode, moduleId, moduleCode));
        }
        Set<Long> rolesSurTous = voulues.stream().filter(v -> v.moduleId() == null).map(HabilitationVoulue::roleId).collect(Collectors.toSet());
        return voulues.stream().filter(v -> v.moduleId() == null || !rolesSurTous.contains(v.roleId())).toList();
    }

    private List<Long> agences(List<Long> demandees) {
        List<Long> agences = demandees == null ? List.of() : demandees.stream().filter(Objects::nonNull).distinct().toList();
        if (agences.isEmpty()) {
            return agences;
        }
        Number connues = (Number) em.createNativeQuery("select count(*) from crm_agence where id in (:ids) and statut = 'ACTIF'")
                .setParameter("ids", agences).getSingleResult();
        if (connues.intValue() != agences.size()) {
            throw new ErreurMetierException("Une des agences choisies est inconnue ou fermée.");
        }
        return agences;
    }

    /** Active les habilitations voulues et desactive les autres ; renvoie vrai si quelque chose a change. */
    private boolean appliquerHabilitations(Long utilisateurId, List<HabilitationVoulue> voulues) {
        @SuppressWarnings("unchecked")
        List<Tuple> existantes = em.createNativeQuery("select id as id, role_id as roleId, module_id as moduleId, actif as actif"
                + " from crm_habilitation where utilisateur_id = :id", Tuple.class).setParameter("id", utilisateurId).getResultList();
        boolean change = false;
        List<Long> aDesactiver = new ArrayList<>();
        List<Long> aActiver = new ArrayList<>();
        List<HabilitationVoulue> restantes = new ArrayList<>(voulues);
        for (Tuple e : existantes) {
            Long roleId = e.get("roleId", Long.class);
            Long moduleId = e.get("moduleId", Long.class);
            boolean actif = vrai(e.get("actif"));
            boolean voulue = restantes.removeIf(v -> v.roleId().equals(roleId) && Objects.equals(v.moduleId(), moduleId));
            if (voulue && !actif) {
                aActiver.add(e.get("id", Long.class));
            } else if (!voulue && actif) {
                aDesactiver.add(e.get("id", Long.class));
            }
        }
        if (!aActiver.isEmpty()) {
            em.createNativeQuery("update crm_habilitation set actif = true where id in (:ids)").setParameter("ids", aActiver).executeUpdate();
            change = true;
        }
        if (!aDesactiver.isEmpty()) {
            em.createNativeQuery("update crm_habilitation set actif = false where id in (:ids)").setParameter("ids", aDesactiver).executeUpdate();
            change = true;
        }
        for (HabilitationVoulue v : restantes) {
            em.createNativeQuery("insert into crm_habilitation (utilisateur_id, role_id, module_id, actif, created_at)"
                    + " values (:utilisateurId, :roleId, :moduleId, true, utc_timestamp())")
                    .setParameter("utilisateurId", utilisateurId)
                    .setParameter("roleId", v.roleId())
                    .setParameter("moduleId", v.moduleId())
                    .executeUpdate();
            change = true;
        }
        return change;
    }

    /** Ouvre les rattachements nouveaux a la date du jour, cloture la veille ceux qui sont retires. */
    private boolean appliquerAgences(Long utilisateurId, List<Long> voulues) {
        @SuppressWarnings("unchecked")
        List<Number> enCours = em.createNativeQuery("""
                select agence_id from crm_utilisateur_agence
                where utilisateur_id = :id and date_debut <= utc_date() and (date_fin is null or date_fin >= utc_date())
                """).setParameter("id", utilisateurId).getResultList();
        Set<Long> actuelles = enCours.stream().map(Number::longValue).collect(Collectors.toSet());
        List<Long> retirees = actuelles.stream().filter(a -> !voulues.contains(a)).toList();
        List<Long> ajoutees = voulues.stream().filter(a -> !actuelles.contains(a)).toList();
        if (!retirees.isEmpty()) {
            em.createNativeQuery("""
                    update crm_utilisateur_agence set date_fin = utc_date() - interval 1 day
                    where utilisateur_id = :id and agence_id in (:agences) and (date_fin is null or date_fin >= utc_date())
                    """).setParameter("id", utilisateurId).setParameter("agences", retirees).executeUpdate();
        }
        for (Long agenceId : ajoutees) {
            em.createNativeQuery("insert into crm_utilisateur_agence (utilisateur_id, agence_id, date_debut, created_at)"
                    + " values (:id, :agenceId, utc_date(), utc_timestamp())")
                    .setParameter("id", utilisateurId)
                    .setParameter("agenceId", agenceId)
                    .executeUpdate();
        }
        return !retirees.isEmpty() || !ajoutees.isEmpty();
    }

    /** Les sessions ouvertes avant cette date devront se reconnecter (voir ControleSessionFiltre). */
    private void reviserDroits(Long id) {
        em.createNativeQuery("update crm_utilisateur set droits_revises_le = utc_timestamp(6) where id = :id").setParameter("id", id).executeUpdate();
    }

    private UtilisateurDTO verrouiller(Long id) {
        @SuppressWarnings("unchecked")
        List<Number> lignes = em.createNativeQuery("select id from crm_utilisateur where id = :id and statut <> 'ARCHIVE' for update")
                .setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Utilisateur introuvable");
        }
        return consulter(id);
    }

    private boolean estAdmin(UtilisateurDTO utilisateur) {
        return utilisateur.getHabilitations().stream().anyMatch(h -> "ADMIN".equals(h.getRoleCode()));
    }

    private void verifierAutreAdmin(Long id) {
        Number autres = (Number) em.createNativeQuery("""
                select count(distinct u.id) from crm_utilisateur u
                join crm_habilitation h on h.utilisateur_id = u.id and h.actif = true
                join crm_role r on r.id = h.role_id and r.code = 'ADMIN'
                where u.statut = 'ACTIF' and u.id <> :id
                """).setParameter("id", id).getSingleResult();
        if (autres.longValue() == 0) {
            throw new ErreurMetierException("Il doit toujours rester au moins un administrateur actif.");
        }
    }

    private String numero(String saisi, Long utilisateurId) {
        if (saisi == null || saisi.isBlank()) {
            return null;
        }
        String normalise = ValeursImport.numeroWhatsapp(saisi, parametreService.valeur("whatsapp.prefixe_pays", "225"))
                .orElseThrow(() -> new ErreurMetierException("Numéro invalide : 10 chiffres attendus, par exemple 07 00 00 00 01."));
        @SuppressWarnings("unchecked")
        List<String> autres = em.createNativeQuery("select nom_complet from crm_utilisateur where numero_whatsapp = :numero and id <> :id")
                .setParameter("numero", normalise)
                .setParameter("id", utilisateurId == null ? -1L : utilisateurId)
                .getResultList();
        if (!autres.isEmpty()) {
            throw new ErreurMetierException("Ce numéro est déjà celui de " + autres.getFirst() + ".");
        }
        return normalise;
    }

    private String motDePasseProvisoire() {
        String tous = LETTRES + CHIFFRES;
        StringBuilder mdp = new StringBuilder();
        mdp.append(LETTRES.charAt(aleatoire.nextInt(LETTRES.length())));
        mdp.append(CHIFFRES.charAt(aleatoire.nextInt(CHIFFRES.length())));
        while (mdp.length() < LONGUEUR_PROVISOIRE) {
            mdp.append(tous.charAt(aleatoire.nextInt(tous.length())));
        }
        // Melange pour ne pas toujours commencer par une lettre puis un chiffre
        for (int i = mdp.length() - 1; i > 0; i--) {
            int j = aleatoire.nextInt(i + 1);
            char c = mdp.charAt(i);
            mdp.setCharAt(i, mdp.charAt(j));
            mdp.setCharAt(j, c);
        }
        return mdp.toString();
    }

    private void completer(List<UtilisateurDTO> utilisateurs) {
        if (utilisateurs.isEmpty()) {
            return;
        }
        List<Long> ids = utilisateurs.stream().map(UtilisateurDTO::getId).toList();
        @SuppressWarnings("unchecked")
        List<Tuple> habilitations = em.createNativeQuery("""
                select h.utilisateur_id as utilisateurId, r.code as roleCode, r.libelle as roleLibelle, m.code as moduleCode, m.libelle as moduleLibelle
                from crm_habilitation h
                join crm_role r on r.id = h.role_id
                left join crm_module m on m.id = h.module_id
                where h.utilisateur_id in (:ids) and h.actif = true
                order by r.id, m.id
                """, Tuple.class).setParameter("ids", ids).getResultList();
        @SuppressWarnings("unchecked")
        List<Tuple> agences = em.createNativeQuery("""
                select ua.utilisateur_id as utilisateurId, a.id as agenceId, a.libelle as libelle, ua.date_debut as depuis
                from crm_utilisateur_agence ua join crm_agence a on a.id = ua.agence_id
                where ua.utilisateur_id in (:ids) and ua.date_debut <= utc_date() and (ua.date_fin is null or ua.date_fin >= utc_date())
                order by a.libelle
                """, Tuple.class).setParameter("ids", ids).getResultList();
        for (UtilisateurDTO u : utilisateurs) {
            u.setHabilitations(habilitations.stream().filter(h -> u.getId().equals(h.get("utilisateurId", Long.class)))
                    .map(h -> HabilitationDTO.builder()
                            .roleCode(h.get("roleCode", String.class))
                            .roleLibelle(h.get("roleLibelle", String.class))
                            .moduleCode(h.get("moduleCode", String.class))
                            .moduleLibelle(h.get("moduleLibelle", String.class))
                            .build())
                    .toList());
            u.setAgences(agences.stream().filter(a -> u.getId().equals(a.get("utilisateurId", Long.class)))
                    .map(a -> AgenceUtilisateurDTO.builder()
                            .agenceId(a.get("agenceId", Long.class))
                            .libelle(a.get("libelle", String.class))
                            .depuis(a.get("depuis", LocalDate.class))
                            .build())
                    .toList());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> codes(String sql) {
        Map<String, Long> codes = new LinkedHashMap<>();
        for (Object[] ligne : (List<Object[]>) em.createNativeQuery(sql).getResultList()) {
            codes.put((String) ligne[0], ((Number) ligne[1]).longValue());
        }
        return codes;
    }

    private String resume(List<HabilitationVoulue> habilitations) {
        return habilitations.stream().map(h -> h.roleCode() + (h.moduleCode() == null ? "" : "/" + h.moduleCode())).collect(Collectors.joining(", "));
    }

    /** BOOLEAN MariaDB : Boolean ou nombre selon le pilote. */
    private static boolean vrai(Object valeur) {
        return valeur instanceof Boolean b ? b : valeur instanceof Number n && n.intValue() != 0;
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static String echapperLike(String valeur) {
        return valeur.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    UtilisateurDTO mapTupleToDto(Tuple tuple) {
        return UtilisateurDTO.builder()
                .id(tuple.get("id", Long.class))
                .login(tuple.get("login", String.class))
                .nomComplet(tuple.get("nomComplet", String.class))
                .email(tuple.get("email", String.class))
                .numeroWhatsapp(tuple.get("numeroWhatsapp", String.class))
                .statut(tuple.get("statut", String.class))
                .motDePasseProvisoire(vrai(tuple.get("motDePasseProvisoire")))
                .echecsConnexion(((Number) tuple.get("echecsConnexion")).intValue())
                .derniereConnexion(tuple.get("derniereConnexion", LocalDateTime.class))
                .createdAt(tuple.get("createdAt", LocalDateTime.class))
                .habilitations(List.of())
                .agences(List.of())
                .build();
    }

    private record HabilitationVoulue(Long roleId, String roleCode, Long moduleId, String moduleCode) {
    }
}
