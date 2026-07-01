package com.ubisenderpro.entity;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Proposition de relance générée par l'assistant (à valider avant envoi).
 */
@Entity
@Table(name = "usp_rec_proposition")
public class RecProposition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "motif", nullable = false, length = 40)
    private String motif;

    @Column(name = "priorite", nullable = false, length = 20)
    private String priorite = "NORMALE";

    @Column(name = "jours_retard")
    private Integer joursRetard;

    @Column(name = "montant", precision = 15, scale = 2)
    private BigDecimal montant;

    @Column(name = "canal_recommande", length = 20)
    private String canalRecommande;

    @Column(name = "modele_id")
    private Long modeleId;

    @Column(name = "statut", nullable = false, length = 20)
    private String statut = "PROPOSEE";

    @Column(name = "cle", length = 120)
    private String cle;

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
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
    public String getPriorite() { return priorite; }
    public void setPriorite(String priorite) { this.priorite = priorite; }
    public Integer getJoursRetard() { return joursRetard; }
    public void setJoursRetard(Integer joursRetard) { this.joursRetard = joursRetard; }
    public BigDecimal getMontant() { return montant; }
    public void setMontant(BigDecimal montant) { this.montant = montant; }
    public String getCanalRecommande() { return canalRecommande; }
    public void setCanalRecommande(String canalRecommande) { this.canalRecommande = canalRecommande; }
    public Long getModeleId() { return modeleId; }
    public void setModeleId(Long modeleId) { this.modeleId = modeleId; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getCle() { return cle; }
    public void setCle(String cle) { this.cle = cle; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
