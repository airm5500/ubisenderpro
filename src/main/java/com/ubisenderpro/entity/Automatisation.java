package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usp_automatisation")
public class Automatisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom", nullable = false, length = 150)
    private String nom;

    @Column(name = "declencheur", nullable = false, length = 60)
    private String declencheur;

    @Column(name = "mot_cle", length = 150)
    private String motCle;

    @Column(name = "condition_json", columnDefinition = "TEXT")
    private String conditionJson;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "action_params_json", columnDefinition = "TEXT")
    private String actionParamsJson;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getDeclencheur() { return declencheur; }
    public void setDeclencheur(String declencheur) { this.declencheur = declencheur; }
    public String getMotCle() { return motCle; }
    public void setMotCle(String motCle) { this.motCle = motCle; }
    public String getConditionJson() { return conditionJson; }
    public void setConditionJson(String conditionJson) { this.conditionJson = conditionJson; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getActionParamsJson() { return actionParamsJson; }
    public void setActionParamsJson(String actionParamsJson) { this.actionParamsJson = actionParamsJson; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
