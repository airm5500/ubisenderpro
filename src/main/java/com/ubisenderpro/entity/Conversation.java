package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usp_conversation")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Canal de la conversation : API (Cloud officielle) ou WEB (WhatsApp Web). */
    @Column(name = "canal", nullable = false, length = 10)
    private String canal = "API";

    @Column(name = "whatsapp_account_id")
    private Long whatsappAccountId;

    @Column(name = "wa_web_session_id")
    private Long waWebSessionId;

    @Column(name = "contact_id")
    private Long contactId;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "numero_whatsapp", nullable = false, length = 25)
    private String numeroWhatsapp;

    @Column(name = "nom_affiche", length = 255)
    private String nomAffiche;

    @Column(name = "statut", nullable = false, length = 20)
    private String statut = "OUVERTE";

    /** Le bot répond tant qu'aucun humain n'a repris la main sur cette conversation. */
    @Column(name = "bot_actif", nullable = false)
    private boolean botActif = true;

    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "non_lu", nullable = false)
    private int nonLu = 0;

    @Column(name = "dernier_message", length = 1000)
    private String dernierMessage;

    @Column(name = "date_dernier_message")
    private LocalDateTime dateDernierMessage;

    @Column(name = "fenetre_expire_at")
    private LocalDateTime fenetreExpireAt;

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
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public Long getWaWebSessionId() { return waWebSessionId; }
    public void setWaWebSessionId(Long waWebSessionId) { this.waWebSessionId = waWebSessionId; }
    public Long getWhatsappAccountId() { return whatsappAccountId; }
    public void setWhatsappAccountId(Long whatsappAccountId) { this.whatsappAccountId = whatsappAccountId; }
    public Long getContactId() { return contactId; }
    public void setContactId(Long contactId) { this.contactId = contactId; }
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public String getNumeroWhatsapp() { return numeroWhatsapp; }
    public void setNumeroWhatsapp(String numeroWhatsapp) { this.numeroWhatsapp = numeroWhatsapp; }
    public String getNomAffiche() { return nomAffiche; }
    public void setNomAffiche(String nomAffiche) { this.nomAffiche = nomAffiche; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public boolean isBotActif() { return botActif; }
    public void setBotActif(boolean botActif) { this.botActif = botActif; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public int getNonLu() { return nonLu; }
    public void setNonLu(int nonLu) { this.nonLu = nonLu; }
    public String getDernierMessage() { return dernierMessage; }
    public void setDernierMessage(String dernierMessage) { this.dernierMessage = dernierMessage; }
    public LocalDateTime getDateDernierMessage() { return dateDernierMessage; }
    public void setDateDernierMessage(LocalDateTime dateDernierMessage) { this.dateDernierMessage = dateDernierMessage; }
    public LocalDateTime getFenetreExpireAt() { return fenetreExpireAt; }
    public void setFenetreExpireAt(LocalDateTime fenetreExpireAt) { this.fenetreExpireAt = fenetreExpireAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
