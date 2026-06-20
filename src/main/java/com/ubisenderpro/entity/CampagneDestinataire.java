package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usp_campagne_destinataire")
public class CampagneDestinataire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campagne_id", nullable = false)
    private Long campagneId;

    @Column(name = "contact_id")
    private Long contactId;

    @Column(name = "numero_whatsapp", length = 25)
    private String numeroWhatsapp;

    @Column(name = "nom_contact", length = 255)
    private String nomContact;

    @Column(name = "statut", nullable = false, length = 20)
    private String statut = "EN_ATTENTE";

    @Column(name = "wa_message_id", length = 150)
    private String waMessageId;

    @Column(name = "erreur", length = 500)
    private String erreur;

    @Column(name = "envoye_at")
    private LocalDateTime envoyeAt;

    @Column(name = "distribue_at")
    private LocalDateTime distribueAt;

    @Column(name = "lu_at")
    private LocalDateTime luAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCampagneId() { return campagneId; }
    public void setCampagneId(Long campagneId) { this.campagneId = campagneId; }
    public Long getContactId() { return contactId; }
    public void setContactId(Long contactId) { this.contactId = contactId; }
    public String getNumeroWhatsapp() { return numeroWhatsapp; }
    public void setNumeroWhatsapp(String numeroWhatsapp) { this.numeroWhatsapp = numeroWhatsapp; }
    public String getNomContact() { return nomContact; }
    public void setNomContact(String nomContact) { this.nomContact = nomContact; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getWaMessageId() { return waMessageId; }
    public void setWaMessageId(String waMessageId) { this.waMessageId = waMessageId; }
    public String getErreur() { return erreur; }
    public void setErreur(String erreur) { this.erreur = erreur; }
    public LocalDateTime getEnvoyeAt() { return envoyeAt; }
    public void setEnvoyeAt(LocalDateTime envoyeAt) { this.envoyeAt = envoyeAt; }
    public LocalDateTime getDistribueAt() { return distribueAt; }
    public void setDistribueAt(LocalDateTime distribueAt) { this.distribueAt = distribueAt; }
    public LocalDateTime getLuAt() { return luAt; }
    public void setLuAt(LocalDateTime luAt) { this.luAt = luAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
