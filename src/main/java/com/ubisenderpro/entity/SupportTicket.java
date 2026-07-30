package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Ticket du Centre de support. Workflow :
 * NOUVEAU → OUVERT → AFFECTE → EN_COURS → EN_ATTENTE_CLIENT → RESOLU → CLOTURE (+ ANNULE).
 */
@Entity
@Table(name = "usp_support_ticket")
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Référence lisible, ex. TCK-2026-0001. */
    @Column(name = "numero", nullable = false, unique = true, length = 30)
    private String numero;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "societe", length = 150)
    private String societe;

    /** Login de l'utilisateur ayant ouvert le ticket. */
    @Column(name = "utilisateur", length = 100)
    private String utilisateur;

    @Column(name = "module", length = 50)
    private String module;

    @Column(name = "type", nullable = false, length = 30)
    private String type = "INCIDENT";

    @Column(name = "priorite", nullable = false, length = 20)
    private String priorite = "NORMALE";

    @Column(name = "sujet", nullable = false)
    private String sujet;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "statut", nullable = false, length = 30)
    private String statut = "NOUVEAU";

    @Column(name = "affecte_a", length = 100)
    private String affecteA;

    /** Signature d'un événement du journal rattaché (bug capturé), le cas échéant. */
    @Column(name = "event_signature", length = 64)
    private String eventSignature;

    @Column(name = "pieces", length = 500)
    private String pieces;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) { createdAt = LocalDateTime.now(); } }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public String getSociete() { return societe; }
    public void setSociete(String societe) { this.societe = societe; }
    public String getUtilisateur() { return utilisateur; }
    public void setUtilisateur(String utilisateur) { this.utilisateur = utilisateur; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPriorite() { return priorite; }
    public void setPriorite(String priorite) { this.priorite = priorite; }
    public String getSujet() { return sujet; }
    public void setSujet(String sujet) { this.sujet = sujet; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getAffecteA() { return affecteA; }
    public void setAffecteA(String affecteA) { this.affecteA = affecteA; }
    public String getEventSignature() { return eventSignature; }
    public void setEventSignature(String eventSignature) { this.eventSignature = eventSignature; }
    public String getPieces() { return pieces; }
    public void setPieces(String pieces) { this.pieces = pieces; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
