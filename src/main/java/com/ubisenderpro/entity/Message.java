package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usp_message")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "wa_message_id", length = 150)
    private String waMessageId;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction; // ENTRANT / SORTANT

    @Column(name = "type_message", nullable = false, length = 20)
    private String typeMessage = "TEXTE";

    @Column(name = "contenu", columnDefinition = "TEXT")
    private String contenu;

    @Column(name = "modele_id")
    private Long modeleId;

    @Column(name = "statut", nullable = false, length = 20)
    private String statut = "ENVOYE";

    @Column(name = "note_interne", nullable = false)
    private boolean noteInterne = false;

    @Column(name = "erreur", length = 500)
    private String erreur;

    @Column(name = "expediteur_id")
    private Long expediteurId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getWaMessageId() { return waMessageId; }
    public void setWaMessageId(String waMessageId) { this.waMessageId = waMessageId; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getTypeMessage() { return typeMessage; }
    public void setTypeMessage(String typeMessage) { this.typeMessage = typeMessage; }
    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }
    public Long getModeleId() { return modeleId; }
    public void setModeleId(Long modeleId) { this.modeleId = modeleId; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public boolean isNoteInterne() { return noteInterne; }
    public void setNoteInterne(boolean noteInterne) { this.noteInterne = noteInterne; }
    public String getErreur() { return erreur; }
    public void setErreur(String erreur) { this.erreur = erreur; }
    public Long getExpediteurId() { return expediteurId; }
    public void setExpediteurId(Long expediteurId) { this.expediteurId = expediteurId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
}
