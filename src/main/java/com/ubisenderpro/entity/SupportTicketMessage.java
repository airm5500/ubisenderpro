package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/** Message de la conversation d'un ticket (même patron que Message d'une discussion). */
@Entity
@Table(name = "usp_support_ticket_message")
public class SupportTicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** CLIENT (utilisateur), INTERNE (équipe support) ou SYSTEME (changements de statut). */
    @Column(name = "direction", nullable = false, length = 10)
    private String direction = "CLIENT";

    @Column(name = "auteur", length = 100)
    private String auteur;

    @Column(name = "corps", nullable = false, columnDefinition = "TEXT")
    private String corps;

    @Column(name = "pieces", length = 500)
    private String pieces;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) { createdAt = LocalDateTime.now(); } }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getAuteur() { return auteur; }
    public void setAuteur(String auteur) { this.auteur = auteur; }
    public String getCorps() { return corps; }
    public void setCorps(String corps) { this.corps = corps; }
    public String getPieces() { return pieces; }
    public void setPieces(String pieces) { this.pieces = pieces; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
