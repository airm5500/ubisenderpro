package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/** Audit local des événements de licence (activation, renouvellement, alertes…). */
@Entity
@Table(name = "usp_licence_evenement")
public class LicenceEvenement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ACTIVATION, RENOUVELLEMENT, REFUS, EXPIRATION, GRACE, HORLOGE, DEMANDE. */
    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "utilisateur", length = 100)
    private String utilisateur;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) { createdAt = LocalDateTime.now(); } }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getUtilisateur() { return utilisateur; }
    public void setUtilisateur(String utilisateur) { this.utilisateur = utilisateur; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
