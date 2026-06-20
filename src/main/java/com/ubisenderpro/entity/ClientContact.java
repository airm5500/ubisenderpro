package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usp_client_contact")
public class ClientContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "nom_complet", nullable = false)
    private String nomComplet;

    @Column(name = "fonction", length = 150)
    private String fonction;

    @Column(name = "telephone_principal", length = 25)
    private String telephonePrincipal;

    @Column(name = "telephone_2", length = 25)
    private String telephone2;

    @Column(name = "numero_whatsapp", length = 25)
    private String numeroWhatsapp;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "contact_principal", nullable = false)
    private boolean contactPrincipal = false;

    @Column(name = "consentement_whatsapp", nullable = false)
    private boolean consentementWhatsapp = false;

    @Column(name = "date_consentement")
    private LocalDateTime dateConsentement;

    @Column(name = "source_consentement", length = 150)
    private String sourceConsentement;

    @Column(name = "desabonne", nullable = false)
    private boolean desabonne = false;

    @Column(name = "bloque", nullable = false)
    private boolean bloque = false;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "derniere_interaction")
    private LocalDateTime derniereInteraction;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }
    public String getFonction() { return fonction; }
    public void setFonction(String fonction) { this.fonction = fonction; }
    public String getTelephonePrincipal() { return telephonePrincipal; }
    public void setTelephonePrincipal(String telephonePrincipal) { this.telephonePrincipal = telephonePrincipal; }
    public String getTelephone2() { return telephone2; }
    public void setTelephone2(String telephone2) { this.telephone2 = telephone2; }
    public String getNumeroWhatsapp() { return numeroWhatsapp; }
    public void setNumeroWhatsapp(String numeroWhatsapp) { this.numeroWhatsapp = numeroWhatsapp; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isContactPrincipal() { return contactPrincipal; }
    public void setContactPrincipal(boolean contactPrincipal) { this.contactPrincipal = contactPrincipal; }
    public boolean isConsentementWhatsapp() { return consentementWhatsapp; }
    public void setConsentementWhatsapp(boolean consentementWhatsapp) { this.consentementWhatsapp = consentementWhatsapp; }
    public LocalDateTime getDateConsentement() { return dateConsentement; }
    public void setDateConsentement(LocalDateTime dateConsentement) { this.dateConsentement = dateConsentement; }
    public String getSourceConsentement() { return sourceConsentement; }
    public void setSourceConsentement(String sourceConsentement) { this.sourceConsentement = sourceConsentement; }
    public boolean isDesabonne() { return desabonne; }
    public void setDesabonne(boolean desabonne) { this.desabonne = desabonne; }
    public boolean isBloque() { return bloque; }
    public void setBloque(boolean bloque) { this.bloque = bloque; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public LocalDateTime getDerniereInteraction() { return derniereInteraction; }
    public void setDerniereInteraction(LocalDateTime derniereInteraction) { this.derniereInteraction = derniereInteraction; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
