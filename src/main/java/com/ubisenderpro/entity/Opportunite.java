package com.ubisenderpro.entity;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "usp_opportunite")
public class Opportunite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "contact_id")
    private Long contactId;

    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "campagne_id")
    private Long campagneId;

    @Column(name = "origine", length = 60)
    private String origine;

    @Column(name = "montant_estime", precision = 15, scale = 2)
    private BigDecimal montantEstime;

    @Column(name = "probabilite")
    private Integer probabilite;

    @Column(name = "prochaine_action", length = 255)
    private String prochaineAction;

    @Column(name = "date_relance")
    private LocalDateTime dateRelance;

    @Column(name = "statut", nullable = false, length = 30)
    private String statut = "NOUVEAU_CONTACT";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Libellés d'affichage (non persistés) : nom du client / de l'agent. */
    @javax.persistence.Transient
    private String clientNom;
    @javax.persistence.Transient
    private String agentNom;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public Long getContactId() { return contactId; }
    public void setContactId(Long contactId) { this.contactId = contactId; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public String getClientNom() { return clientNom; }
    public void setClientNom(String clientNom) { this.clientNom = clientNom; }
    public String getAgentNom() { return agentNom; }
    public void setAgentNom(String agentNom) { this.agentNom = agentNom; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getCampagneId() { return campagneId; }
    public void setCampagneId(Long campagneId) { this.campagneId = campagneId; }
    public String getOrigine() { return origine; }
    public void setOrigine(String origine) { this.origine = origine; }
    public BigDecimal getMontantEstime() { return montantEstime; }
    public void setMontantEstime(BigDecimal montantEstime) { this.montantEstime = montantEstime; }
    public Integer getProbabilite() { return probabilite; }
    public void setProbabilite(Integer probabilite) { this.probabilite = probabilite; }
    public String getProchaineAction() { return prochaineAction; }
    public void setProchaineAction(String prochaineAction) { this.prochaineAction = prochaineAction; }
    public LocalDateTime getDateRelance() { return dateRelance; }
    public void setDateRelance(LocalDateTime dateRelance) { this.dateRelance = dateRelance; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
