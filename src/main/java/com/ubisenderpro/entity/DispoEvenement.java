package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Événement de disponibilité ou de rupture (module Disponibilités & Ruptures).
 * Regroupe un ou plusieurs produits ({@link DispoProduit}) et sert de source
 * aux annonces / propositions d'envoi.
 */
@Entity
@Table(name = "usp_dispo_evenement")
public class DispoEvenement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    /** ANNONCE_DISPONIBILITE | RETOUR_RUPTURE | RISQUE_RUPTURE | RUPTURE_CONFIRMEE | STOCK_LIMITE. */
    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "titre", nullable = false, length = 200)
    private String titre;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "date_debut")
    private LocalDateTime dateDebut;

    @Column(name = "date_fin")
    private LocalDateTime dateFin;

    @Column(name = "agence", length = 150)
    private String agence;

    @Column(name = "societe", length = 150)
    private String societe;

    /** TOUS_LES_SEGMENTS | DIAMOND | PLATINIUM | DIAMOND_ET_PLATINIUM | SEGMENTS_SELECTIONNES | ... */
    @Column(name = "audience", length = 40)
    private String audience;

    @Column(name = "segmentation_id")
    private Long segmentationId;

    /** Sélection multiple de segments (IDs de segmentation séparés par des virgules). */
    @Column(name = "segmentation_ids", length = 255)
    private String segmentationIds;

    @Column(name = "region", length = 150)
    private String region;

    @Column(name = "tournee", length = 150)
    private String tournee;

    @Column(name = "liste_id")
    private Long listeId;

    @Column(name = "contact_ids", length = 2000)
    private String contactIds;

    @Column(name = "canal", length = 10)
    private String canal;

    @Column(name = "modele_id")
    private Long modeleId;

    /** BROUILLON | PROGRAMMEE | ENVOYEE | ANNULEE | ARCHIVEE. */
    @Column(name = "statut", length = 20)
    private String statut;

    @Column(name = "responsable", length = 150)
    private String responsable;

    @Column(name = "cree_par", length = 100)
    private String creePar;

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
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDateTime dateDebut) { this.dateDebut = dateDebut; }
    public LocalDateTime getDateFin() { return dateFin; }
    public void setDateFin(LocalDateTime dateFin) { this.dateFin = dateFin; }
    public String getAgence() { return agence; }
    public void setAgence(String agence) { this.agence = agence; }
    public String getSociete() { return societe; }
    public void setSociete(String societe) { this.societe = societe; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public Long getSegmentationId() { return segmentationId; }
    public void setSegmentationId(Long segmentationId) { this.segmentationId = segmentationId; }
    public String getSegmentationIds() { return segmentationIds; }
    public void setSegmentationIds(String segmentationIds) { this.segmentationIds = segmentationIds; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getTournee() { return tournee; }
    public void setTournee(String tournee) { this.tournee = tournee; }
    public Long getListeId() { return listeId; }
    public void setListeId(Long listeId) { this.listeId = listeId; }
    public String getContactIds() { return contactIds; }
    public void setContactIds(String contactIds) { this.contactIds = contactIds; }
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public Long getModeleId() { return modeleId; }
    public void setModeleId(Long modeleId) { this.modeleId = modeleId; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getResponsable() { return responsable; }
    public void setResponsable(String responsable) { this.responsable = responsable; }
    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
