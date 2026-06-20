package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usp_webhook_event")
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source", nullable = false, length = 40)
    private String source = "WHATSAPP";

    @Column(name = "type_event", length = 60)
    private String typeEvent;

    @Column(name = "payload", columnDefinition = "LONGTEXT")
    private String payload;

    @Column(name = "traite", nullable = false)
    private boolean traite = false;

    @Column(name = "erreur", length = 500)
    private String erreur;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getTypeEvent() { return typeEvent; }
    public void setTypeEvent(String typeEvent) { this.typeEvent = typeEvent; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public boolean isTraite() { return traite; }
    public void setTraite(boolean traite) { this.traite = traite; }
    public String getErreur() { return erreur; }
    public void setErreur(String erreur) { this.erreur = erreur; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
