package com.ubisenderpro.entity;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Fiche recouvrement d'un client (complément financier). Liée au client par
 * {@code clientId} (lien logique vers usp_client), sans modifier la table Client.
 */
@Entity
@Table(name = "usp_rec_fiche")
public class RecFiche {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "segment_commercial", length = 150)
    private String segmentCommercial;

    @Column(name = "profil_paiement", length = 150)
    private String profilPaiement;

    @Column(name = "responsable", length = 150)
    private String responsable;

    @Column(name = "statut", length = 60)
    private String statut;

    @Column(name = "canal_prefere", length = 20)
    private String canalPrefere;

    @Column(name = "observations", columnDefinition = "TEXT")
    private String observations;

    @Column(name = "encours_initial", nullable = false, precision = 15, scale = 2)
    private BigDecimal encoursInitial = BigDecimal.ZERO;

    @Column(name = "date_situation")
    private LocalDate dateSituation;

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
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public String getSegmentCommercial() { return segmentCommercial; }
    public void setSegmentCommercial(String segmentCommercial) { this.segmentCommercial = segmentCommercial; }
    public String getProfilPaiement() { return profilPaiement; }
    public void setProfilPaiement(String profilPaiement) { this.profilPaiement = profilPaiement; }
    public String getResponsable() { return responsable; }
    public void setResponsable(String responsable) { this.responsable = responsable; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getCanalPrefere() { return canalPrefere; }
    public void setCanalPrefere(String canalPrefere) { this.canalPrefere = canalPrefere; }
    public String getObservations() { return observations; }
    public void setObservations(String observations) { this.observations = observations; }
    public BigDecimal getEncoursInitial() { return encoursInitial; }
    public void setEncoursInitial(BigDecimal encoursInitial) { this.encoursInitial = encoursInitial; }
    public LocalDate getDateSituation() { return dateSituation; }
    public void setDateSituation(LocalDate dateSituation) { this.dateSituation = dateSituation; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
