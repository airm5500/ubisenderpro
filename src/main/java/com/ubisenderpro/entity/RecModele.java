package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Modèle de message du module Recouvrement (relance préventive, facture échue,
 * impayé, mise en demeure, divers). Variables finance + compatibilité templates Meta.
 */
@Entity
@Table(name = "usp_rec_modele")
public class RecModele {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "nom", nullable = false, length = 150)
    private String nom;

    @Column(name = "type", nullable = false, length = 30)
    private String type = "DIVERS";

    @Column(name = "canal", nullable = false, length = 20)
    private String canal = "TOUS";

    @Column(name = "sujet", length = 255)
    private String sujet;

    @Column(name = "corps", nullable = false, columnDefinition = "TEXT")
    private String corps;

    @Column(name = "nom_modele_whatsapp", length = 150)
    private String nomModeleWhatsapp;

    @Column(name = "params_corps", length = 500)
    private String paramsCorps;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public String getSujet() { return sujet; }
    public void setSujet(String sujet) { this.sujet = sujet; }
    public String getCorps() { return corps; }
    public void setCorps(String corps) { this.corps = corps; }
    public String getNomModeleWhatsapp() { return nomModeleWhatsapp; }
    public void setNomModeleWhatsapp(String nomModeleWhatsapp) { this.nomModeleWhatsapp = nomModeleWhatsapp; }
    public String getParamsCorps() { return paramsCorps; }
    public void setParamsCorps(String paramsCorps) { this.paramsCorps = paramsCorps; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
