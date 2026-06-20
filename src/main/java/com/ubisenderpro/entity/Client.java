package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usp_client")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_client", nullable = false, unique = true, length = 50)
    private String numeroClient;

    @Column(name = "nom_compte", nullable = false)
    private String nomCompte;

    @Column(name = "agence", length = 100)
    private String agence;

    @Column(name = "region", length = 150)
    private String region;

    @Column(name = "email_principal", length = 150)
    private String emailPrincipal;

    @Column(name = "segmentation_id")
    private Long segmentationId;

    @Column(name = "adresse", length = 500)
    private String adresse;

    @Column(name = "ville", length = 100)
    private String ville;

    @Column(name = "commune", length = 100)
    private String commune;

    @Column(name = "pays", length = 100)
    private String pays;

    @Column(name = "statut", nullable = false, length = 30)
    private String statut = "ACTIF";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNumeroClient() { return numeroClient; }
    public void setNumeroClient(String numeroClient) { this.numeroClient = numeroClient; }
    public String getNomCompte() { return nomCompte; }
    public void setNomCompte(String nomCompte) { this.nomCompte = nomCompte; }
    public String getAgence() { return agence; }
    public void setAgence(String agence) { this.agence = agence; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getEmailPrincipal() { return emailPrincipal; }
    public void setEmailPrincipal(String emailPrincipal) { this.emailPrincipal = emailPrincipal; }
    public Long getSegmentationId() { return segmentationId; }
    public void setSegmentationId(Long segmentationId) { this.segmentationId = segmentationId; }
    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }
    public String getVille() { return ville; }
    public void setVille(String ville) { this.ville = ville; }
    public String getCommune() { return commune; }
    public void setCommune(String commune) { this.commune = commune; }
    public String getPays() { return pays; }
    public void setPays(String pays) { this.pays = pays; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
