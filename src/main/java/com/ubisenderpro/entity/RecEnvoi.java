package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Trace d'un envoi de relance (historique du module Recouvrement).
 */
@Entity
@Table(name = "usp_rec_envoi")
public class RecEnvoi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "modele_id")
    private Long modeleId;

    @Column(name = "canal", nullable = false, length = 20)
    private String canal;

    @Column(name = "destinataire", length = 255)
    private String destinataire;

    @Column(name = "sujet", length = 255)
    private String sujet;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "statut", nullable = false, length = 20)
    private String statut;

    @Column(name = "erreur", length = 500)
    private String erreur;

    @Column(name = "wa_message_id", length = 120)
    private String waMessageId;

    @Column(name = "cree_par", length = 100)
    private String creePar;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public Long getModeleId() { return modeleId; }
    public void setModeleId(Long modeleId) { this.modeleId = modeleId; }
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public String getDestinataire() { return destinataire; }
    public void setDestinataire(String destinataire) { this.destinataire = destinataire; }
    public String getSujet() { return sujet; }
    public void setSujet(String sujet) { this.sujet = sujet; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getErreur() { return erreur; }
    public void setErreur(String erreur) { this.erreur = erreur; }
    public String getWaMessageId() { return waMessageId; }
    public void setWaMessageId(String waMessageId) { this.waMessageId = waMessageId; }
    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
