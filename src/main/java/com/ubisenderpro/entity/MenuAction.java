package com.ubisenderpro.entity;

import javax.persistence.*;

/**
 * Action disponible sur un menu (VOIR, CREER, MODIFIER, SUPPRIMER, DESACTIVER,
 * ENVOYER…), avec un libellé explicite affiché dans l'écran des permissions.
 */
@Entity
@Table(name = "usp_menu_action")
public class MenuAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_code", nullable = false, length = 50)
    private String menuCode;

    @Column(name = "action_code", nullable = false, length = 30)
    private String actionCode;

    @Column(name = "libelle", nullable = false, length = 100)
    private String libelle;

    @Column(name = "ordre", nullable = false)
    private int ordre;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMenuCode() { return menuCode; }
    public void setMenuCode(String menuCode) { this.menuCode = menuCode; }
    public String getActionCode() { return actionCode; }
    public void setActionCode(String actionCode) { this.actionCode = actionCode; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public int getOrdre() { return ordre; }
    public void setOrdre(int ordre) { this.ordre = ordre; }
}
