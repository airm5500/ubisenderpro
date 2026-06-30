package com.ubisenderpro.entity;

import javax.persistence.*;

/**
 * Permission accordée à un rôle (par code) : autorisation d'effectuer une
 * {@code actionCode} sur un {@code menuCode}.
 */
@Entity
@Table(name = "usp_role_permission")
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_code", nullable = false, length = 50)
    private String roleCode;

    @Column(name = "menu_code", nullable = false, length = 50)
    private String menuCode;

    @Column(name = "action_code", nullable = false, length = 30)
    private String actionCode;

    public RolePermission() {}

    public RolePermission(String roleCode, String menuCode, String actionCode) {
        this.roleCode = roleCode;
        this.menuCode = menuCode;
        this.actionCode = actionCode;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getMenuCode() { return menuCode; }
    public void setMenuCode(String menuCode) { this.menuCode = menuCode; }
    public String getActionCode() { return actionCode; }
    public void setActionCode(String actionCode) { this.actionCode = actionCode; }
}
