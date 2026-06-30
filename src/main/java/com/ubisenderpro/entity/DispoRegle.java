package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Règle de programmation du risque de rupture (§11) : jour du mois, heure,
 * audience et canal, configurables.
 */
@Entity
@Table(name = "usp_dispo_regle")
public class DispoRegle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "libelle", nullable = false, length = 150)
    private String libelle;

    /** Type d'événement visé (RISQUE_RUPTURE par défaut). */
    @Column(name = "type", nullable = false, length = 30)
    private String type = "RISQUE_RUPTURE";

    @Column(name = "jour_mois", nullable = false)
    private int jourMois;

    @Column(name = "heure", nullable = false)
    private int heure = 8;

    @Column(name = "audience", length = 40)
    private String audience;

    @Column(name = "canal", length = 10)
    private String canal = "WEB";

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
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public int getJourMois() { return jourMois; }
    public void setJourMois(int jourMois) { this.jourMois = jourMois; }
    public int getHeure() { return heure; }
    public void setHeure(int heure) { this.heure = heure; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
