package com.ubisenderpro.entity;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "usp_commande")
public class Commande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_commande", nullable = false, unique = true, length = 50)
    private String numeroCommande;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "contact_id")
    private Long contactId;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "campagne_id")
    private Long campagneId;

    @Column(name = "utilisateur_id")
    private Long utilisateurId;

    @Column(name = "statut", nullable = false, length = 40)
    private String statut = "BROUILLON";

    @Column(name = "date_commande", nullable = false)
    private LocalDateTime dateCommande;

    @Column(name = "montant_brut", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantBrut = BigDecimal.ZERO;

    @Column(name = "montant_remise", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantRemise = BigDecimal.ZERO;

    @Column(name = "montant_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantTotal = BigDecimal.ZERO;

    @Column(name = "mode_retrait", length = 50)
    private String modeRetrait;

    @Column(name = "adresse_livraison", length = 500)
    private String adresseLivraison;

    @Column(name = "mode_paiement", length = 50)
    private String modePaiement;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (dateCommande == null) dateCommande = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNumeroCommande() { return numeroCommande; }
    public void setNumeroCommande(String numeroCommande) { this.numeroCommande = numeroCommande; }
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public Long getContactId() { return contactId; }
    public void setContactId(Long contactId) { this.contactId = contactId; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getCampagneId() { return campagneId; }
    public void setCampagneId(Long campagneId) { this.campagneId = campagneId; }
    public Long getUtilisateurId() { return utilisateurId; }
    public void setUtilisateurId(Long utilisateurId) { this.utilisateurId = utilisateurId; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public LocalDateTime getDateCommande() { return dateCommande; }
    public void setDateCommande(LocalDateTime dateCommande) { this.dateCommande = dateCommande; }
    public BigDecimal getMontantBrut() { return montantBrut; }
    public void setMontantBrut(BigDecimal montantBrut) { this.montantBrut = montantBrut; }
    public BigDecimal getMontantRemise() { return montantRemise; }
    public void setMontantRemise(BigDecimal montantRemise) { this.montantRemise = montantRemise; }
    public BigDecimal getMontantTotal() { return montantTotal; }
    public void setMontantTotal(BigDecimal montantTotal) { this.montantTotal = montantTotal; }
    public String getModeRetrait() { return modeRetrait; }
    public void setModeRetrait(String modeRetrait) { this.modeRetrait = modeRetrait; }
    public String getAdresseLivraison() { return adresseLivraison; }
    public void setAdresseLivraison(String adresseLivraison) { this.adresseLivraison = adresseLivraison; }
    public String getModePaiement() { return modePaiement; }
    public void setModePaiement(String modePaiement) { this.modePaiement = modePaiement; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
