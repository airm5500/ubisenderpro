package com.ubisenderpro.service;

import com.ubisenderpro.entity.Menu;
import com.ubisenderpro.entity.MenuAction;
import com.ubisenderpro.entity.RolePermission;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RBAC fin : menus dynamiques, actions par menu et permissions par rôle.
 *
 * <p>Le catalogue (menus + actions) et les permissions par défaut des rôles
 * existants sont semés au démarrage de façon idempotente, en reproduisant les
 * droits actuels (mappage menu→rôles identique à celui de l'interface), afin
 * d'éviter toute régression. Le rôle {@code ADMIN} dispose toujours de tous les
 * droits.</p>
 */
@Stateless
public class PermissionService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public static final String ADMIN = "ADMIN";

    /** Menus (code, libellé) dans l'ordre d'affichage. */
    private static final String[][] MENUS = {
            {"dashboard", "Tableau de bord"},
            {"inbox", "Discussions"},
            {"clients", "Comptes clients"},
            {"catalogue", "Catalogue"},
            {"promotions", "Promotions"},
            {"marketing", "Marketing"},
            {"dispo", "Disponibilités & Ruptures"},
            {"infos", "Informations Clients"},
            {"campaigns", "Campagnes"},
            {"waweb", "WhatsApp Web"},
            {"historique", "Historique des envois"},
            {"crm", "CRM / Opportunités"},
            {"settings", "Paramètres"},
            {"users", "Utilisateurs"}
    };

    /** Libellés explicites des actions. */
    private static final Map<String, String> LIB_ACTION = new LinkedHashMap<>();
    static {
        LIB_ACTION.put("VOIR", "Voir / accéder");
        LIB_ACTION.put("CREER", "Créer");
        LIB_ACTION.put("MODIFIER", "Modifier");
        LIB_ACTION.put("SUPPRIMER", "Supprimer");
        LIB_ACTION.put("DESACTIVER", "Activer / Désactiver");
        LIB_ACTION.put("ENVOYER", "Envoyer");
        LIB_ACTION.put("EXPORTER", "Exporter");
        LIB_ACTION.put("AJUSTER_STOCK", "Ajuster le stock");
        LIB_ACTION.put("MAJ_PROMO", "Mettre à jour une promo");
        LIB_ACTION.put("VOIR_CONTENU", "Voir le contenu des discussions");
        LIB_ACTION.put("VOIR_DETAILS", "Voir les détails");
        LIB_ACTION.put("ENVOI_MASSE", "Envoyer en masse");
        LIB_ACTION.put("RENVOI_ECHECS", "Renvoyer après échec");
    }

    private static final List<String> ROLES =
            Arrays.asList("ADMIN", "MARKETING", "SUPERVISEUR", "AGENT", "CATALOGUE", "LECTURE");

    /** Actions disponibles pour un menu donné (VOIR toujours présent). */
    private static List<String> actionsDuMenu(String code) {
        List<String> a = new ArrayList<>();
        a.add("VOIR");
        a.add("VOIR_DETAILS"); // privilège « voir les détails » (transverse, point 5)
        if (Arrays.asList("clients", "catalogue", "promotions", "marketing", "dispo",
                "infos", "users", "campaigns").contains(code)) {
            a.add("CREER"); a.add("MODIFIER"); a.add("SUPPRIMER"); a.add("DESACTIVER");
        }
        if (Arrays.asList("campaigns", "marketing", "waweb").contains(code)) { a.add("ENVOYER"); }
        if (Arrays.asList("clients", "historique").contains(code)) { a.add("EXPORTER"); }
        if ("settings".equals(code)) { if (!a.contains("MODIFIER")) { a.add("MODIFIER"); } a.add("SUPPRIMER"); }
        // Actions spécifiques (point 11 / 5).
        if ("catalogue".equals(code)) { a.add("AJUSTER_STOCK"); a.add("MAJ_PROMO"); }
        if ("inbox".equals(code)) { a.add("CREER"); a.add("VOIR_CONTENU"); }
        if (Arrays.asList("campaigns", "waweb").contains(code)) { a.add("ENVOI_MASSE"); a.add("RENVOI_ECHECS"); }
        return a;
    }

    /** Rôles autorisés par défaut sur un menu (reproduit l'interface actuelle). */
    private static List<String> rolesDuMenu(String code) {
        switch (code) {
            case "dashboard": return ROLES;
            case "inbox": return Arrays.asList("ADMIN", "MARKETING", "SUPERVISEUR", "AGENT");
            case "clients": return Arrays.asList("ADMIN", "MARKETING", "SUPERVISEUR", "AGENT", "LECTURE");
            case "catalogue": return Arrays.asList("ADMIN", "CATALOGUE", "LECTURE");
            case "promotions": return Arrays.asList("ADMIN", "MARKETING", "CATALOGUE");
            case "marketing": return Arrays.asList("ADMIN", "MARKETING", "CATALOGUE");
            case "dispo": return Arrays.asList("ADMIN", "MARKETING", "CATALOGUE");
            case "infos": return Arrays.asList("ADMIN", "MARKETING", "SUPERVISEUR");
            case "campaigns": return Arrays.asList("ADMIN", "MARKETING");
            case "waweb": return Arrays.asList("ADMIN", "MARKETING");
            case "historique": return Arrays.asList("ADMIN", "MARKETING");
            case "crm": return Arrays.asList("ADMIN", "SUPERVISEUR", "AGENT", "MARKETING");
            case "settings": return Arrays.asList("ADMIN");
            case "users": return Arrays.asList("ADMIN");
            default: return Arrays.asList("ADMIN");
        }
    }

    /* ------------------------- Initialisation (Bootstrap) ------------------------- */

    /**
     * Sème/complète menus, actions et permissions, de façon <b>incrémentale et
     * idempotente</b> : les menus et actions manquants sont ajoutés (ce qui permet
     * d'introduire de nouvelles actions sans migration), ADMIN reçoit toujours tous
     * les droits, et les rôles existants ne sont initialisés que la première fois
     * (pour ne pas écraser les réglages faits dans l'écran « Rôles & permissions »).
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int initPermissions() {
        int crees = 0;
        int ordre = 1;
        java.util.List<String[]> nouvellesActions = new java.util.ArrayList<>();
        for (String[] mc : MENUS) {
            String code = mc[0];
            // Menu manquant -> créé.
            if (em.createQuery("SELECT COUNT(m) FROM Menu m WHERE m.code = :c", Long.class)
                    .setParameter("c", code).getSingleResult() == 0) {
                Menu m = new Menu();
                m.setCode(code); m.setLibelle(mc[1]); m.setOrdre(ordre); m.setActif(true);
                em.persist(m);
                crees++;
            }
            ordre++;
            // Actions manquantes -> créées (mémorisées pour l'octroi aux rôles « writers »).
            int oa = 1;
            for (String act : actionsDuMenu(code)) {
                if (em.createQuery("SELECT COUNT(a) FROM MenuAction a WHERE a.menuCode = :m AND a.actionCode = :a", Long.class)
                        .setParameter("m", code).setParameter("a", act).getSingleResult() == 0) {
                    MenuAction ma = new MenuAction();
                    ma.setMenuCode(code); ma.setActionCode(act);
                    ma.setLibelle(LIB_ACTION.getOrDefault(act, act)); ma.setOrdre(oa);
                    em.persist(ma);
                    nouvellesActions.add(new String[]{code, act});
                }
                oa++;
            }
            // ADMIN : toujours toutes les permissions (y compris les nouvelles).
            for (String act : actionsDuMenu(code)) {
                if (!permExiste(ADMIN, code, act)) { em.persist(new RolePermission(ADMIN, code, act)); }
            }
        }
        // Nouvelle action sur un menu déjà en service : l'accorder aux rôles qui peuvent
        // déjà y créer (writers), une seule fois, pour ne pas régresser leurs droits.
        for (String[] na : nouvellesActions) {
            if ("VOIR".equals(na[1])) { continue; }
            // Rôles qui voient déjà le menu (ils pouvaient agir avant l'ajout) — sauf
            // ADMIN (déjà tout) et LECTURE (lecture seule).
            List<String> viewers = em.createQuery(
                    "SELECT DISTINCT p.roleCode FROM RolePermission p WHERE p.menuCode = :m AND p.actionCode = 'VOIR'",
                    String.class).setParameter("m", na[0]).getResultList();
            for (String r : viewers) {
                if (ADMIN.equals(r) || "LECTURE".equals(r)) { continue; }
                if (!permExiste(r, na[0], na[1])) { em.persist(new RolePermission(r, na[0], na[1])); }
            }
        }
        // Rôles non-admin : initialisation par défaut uniquement à la première fois.
        for (String role : ROLES) {
            if (ADMIN.equals(role)) { continue; }
            Long nb = em.createQuery("SELECT COUNT(p) FROM RolePermission p WHERE p.roleCode = :r", Long.class)
                    .setParameter("r", role).getSingleResult();
            if (nb > 0) { continue; }
            for (String[] mc : MENUS) {
                String code = mc[0];
                if (!rolesDuMenu(code).contains(role)) { continue; }
                for (String act : actionsDuMenu(code)) {
                    if ("LECTURE".equals(role) && !"VOIR".equals(act)) { continue; }
                    em.persist(new RolePermission(role, code, act));
                }
            }
        }
        return crees;
    }

    private boolean permExiste(String role, String menu, String action) {
        return em.createQuery("SELECT COUNT(p) FROM RolePermission p WHERE p.roleCode = :r " +
                "AND p.menuCode = :m AND p.actionCode = :a", Long.class)
                .setParameter("r", role).setParameter("m", menu).setParameter("a", action)
                .getSingleResult() > 0;
    }

    /* ------------------------- Lecture (UI) ------------------------- */

    /** Menus actifs avec leurs actions, pour l'écran des permissions. */
    public List<Map<String, Object>> listerMenus() {
        List<Menu> menus = em.createQuery(
                "SELECT m FROM Menu m WHERE m.actif = true ORDER BY m.ordre", Menu.class).getResultList();
        List<MenuAction> actions = em.createQuery(
                "SELECT a FROM MenuAction a ORDER BY a.menuCode, a.ordre", MenuAction.class).getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Menu m : menus) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("code", m.getCode());
            mm.put("libelle", m.getLibelle());
            List<Map<String, Object>> acts = new ArrayList<>();
            for (MenuAction a : actions) {
                if (a.getMenuCode().equals(m.getCode())) {
                    Map<String, Object> am = new LinkedHashMap<>();
                    am.put("code", a.getActionCode());
                    am.put("libelle", a.getLibelle());
                    acts.add(am);
                }
            }
            mm.put("actions", acts);
            out.add(mm);
        }
        return out;
    }

    /** Permissions d'un rôle sous forme « menu:action ». */
    public List<String> permissionsRole(String roleCode) {
        List<RolePermission> l = em.createQuery(
                "SELECT p FROM RolePermission p WHERE p.roleCode = :r", RolePermission.class)
                .setParameter("r", roleCode).getResultList();
        List<String> out = new ArrayList<>();
        for (RolePermission p : l) { out.add(p.getMenuCode() + ":" + p.getActionCode()); }
        return out;
    }

    /** Permissions effectives d'un ensemble de rôles : menu -> actions (ADMIN = tout). */
    public Map<String, Set<String>> effectives(Collection<String> roles) {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        boolean admin = roles != null && roles.contains(ADMIN);
        if (admin) {
            for (String[] mc : MENUS) { map.put(mc[0], new LinkedHashSet<>(actionsDuMenu(mc[0]))); }
            return map;
        }
        if (roles == null || roles.isEmpty()) { return map; }
        List<RolePermission> l = em.createQuery(
                "SELECT p FROM RolePermission p WHERE p.roleCode IN :r", RolePermission.class)
                .setParameter("r", roles).getResultList();
        for (RolePermission p : l) {
            map.computeIfAbsent(p.getMenuCode(), k -> new LinkedHashSet<>()).add(p.getActionCode());
        }
        return map;
    }

    /** Vrai si l'un des rôles autorise (menu, action). ADMIN autorisé d'office. */
    public boolean autorise(Collection<String> roles, String menu, String action) {
        if (roles != null && roles.contains(ADMIN)) { return true; }
        if (roles == null || roles.isEmpty()) { return false; }
        Long n = em.createQuery(
                "SELECT COUNT(p) FROM RolePermission p WHERE p.roleCode IN :r " +
                "AND p.menuCode = :m AND p.actionCode = :a", Long.class)
                .setParameter("r", roles).setParameter("m", menu).setParameter("a", action)
                .getSingleResult();
        return n != null && n > 0;
    }

    /* ------------------------- Écriture (admin) ------------------------- */

    /** Remplace l'ensemble des permissions d'un rôle par la liste « menu:action » fournie. */
    public void definirPermissionsRole(String roleCode, List<String> permissions) {
        if (roleCode == null || roleCode.trim().isEmpty()) {
            throw new ValidationException("role", "Le rôle est obligatoire.");
        }
        em.createQuery("DELETE FROM RolePermission p WHERE p.roleCode = :r")
                .setParameter("r", roleCode).executeUpdate();
        if (permissions == null) { return; }
        Set<String> uniques = new LinkedHashSet<>(permissions);
        for (String mp : uniques) {
            if (mp == null || !mp.contains(":")) { continue; }
            String[] parts = mp.split(":", 2);
            em.persist(new RolePermission(roleCode, parts[0].trim(), parts[1].trim()));
        }
    }
}
