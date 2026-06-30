package com.ubisenderpro.entity;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Règlement (paiement) d'un client, éventuellement rattaché à une créance.
 */
@Entity
@Table(name = "usp_rec_paiement")
public class RecPaiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "creance_id")
    private Long creanceId;

    @Column(name = "date_paiement")
    private LocalDate datePaiement;

    @Column(name = "montant", nullable = false, precision = 15, scale = 2)
    private BigDecimal montant = BigDecimal.ZERO;

    @Column(name = "mode", length = 40)
    private String mode;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public Long getCreanceId() { return creanceId; }
    public void setCreanceId(Long creanceId) { this.creanceId = creanceId; }
    public LocalDate getDatePaiement() { return datePaiement; }
    public void setDatePaiement(LocalDate datePaiement) { this.datePaiement = datePaiement; }
    public BigDecimal getMontant() { return montant; }
    public void setMontant(BigDecimal montant) { this.montant = montant; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
