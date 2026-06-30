-- =====================================================================
-- UbiSenderPro - Menus dynamiques & permissions (RBAC fin)
-- Rôle = libellé regroupant des permissions ; permission = (menu, action).
-- Les menus, leurs actions et les permissions par défaut des rôles existants
-- sont semés au démarrage (PermissionService.initPermissions), de façon
-- idempotente, pour reproduire les droits actuels sans régression.
-- =====================================================================

-- Menus de l'application (dynamiques : on peut en ajouter à tout moment).
CREATE TABLE IF NOT EXISTS usp_menu (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    ordre INT NOT NULL DEFAULT 0,
    actif TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_menu_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Actions disponibles par menu (VOIR, CREER, MODIFIER, SUPPRIMER, DESACTIVER, ENVOYER…).
CREATE TABLE IF NOT EXISTS usp_menu_action (
    id BIGINT NOT NULL AUTO_INCREMENT,
    menu_code VARCHAR(50) NOT NULL,
    action_code VARCHAR(30) NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    ordre INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_menu_action (menu_code, action_code),
    KEY idx_usp_menu_action_menu (menu_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Permissions accordées à un rôle (par code de rôle).
-- NB : nom distinct de l'historique usp_role_permission (socle V1 : role_id/permission_id),
-- pour éviter toute collision avec la table héritée.
CREATE TABLE IF NOT EXISTS usp_role_menu_action (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_code VARCHAR(50) NOT NULL,
    menu_code VARCHAR(50) NOT NULL,
    action_code VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_rma (role_code, menu_code, action_code),
    KEY idx_usp_rma_role (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
