package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Événement applicatif capturé (journal de diagnostic du Centre de support).
 * Dédoublonné par {@code signature} : une même erreur incrémente
 * {@code occurrences} au lieu de créer une ligne.
 */
@Entity
@Table(name = "usp_application_event")
public class ApplicationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "module", length = 50)
    private String module;

    /** EXCEPTION_JAVA, SQL, JS, API, WHATSAPP, EMAIL, IMPORT… */
    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "niveau", nullable = false, length = 10)
    private String niveau = "ERROR";

    /** Hash court servant de clé de dédoublonnage. */
    @Column(name = "signature", nullable = false, unique = true, length = 64)
    private String signature;

    @Column(name = "message_court", length = 500)
    private String messageCourt;

    @Column(name = "occurrences", nullable = false)
    private int occurrences = 1;

    @Column(name = "utilisateur", length = 100)
    private String utilisateur;

    @Column(name = "url_ou_ecran", length = 255)
    private String urlOuEcran;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) { createdAt = now; }
        if (lastSeenAt == null) { lastSeenAt = now; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getNiveau() { return niveau; }
    public void setNiveau(String niveau) { this.niveau = niveau; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public String getMessageCourt() { return messageCourt; }
    public void setMessageCourt(String messageCourt) { this.messageCourt = messageCourt; }
    public int getOccurrences() { return occurrences; }
    public void setOccurrences(int occurrences) { this.occurrences = occurrences; }
    public String getUtilisateur() { return utilisateur; }
    public void setUtilisateur(String utilisateur) { this.utilisateur = utilisateur; }
    public String getUrlOuEcran() { return urlOuEcran; }
    public void setUrlOuEcran(String urlOuEcran) { this.urlOuEcran = urlOuEcran; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
