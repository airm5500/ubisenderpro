package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** État de licence courant (une ligne active ; le catalogue vit chez l'éditeur). */
@Entity
@Table(name = "usp_licence")
public class Licence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", length = 60)
    private String clientId;

    @Column(name = "societe", length = 150)
    private String societe;

    @Column(name = "pays", length = 100)
    private String pays;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "type", length = 30)
    private String type;

    @Column(name = "date_activation")
    private LocalDate dateActivation;

    @Column(name = "date_expiration")
    private LocalDate dateExpiration;

    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "max_agences")
    private Integer maxAgences;

    /** Codes de menus/capacités autorisés, en CSV (vide = tous). */
    @Column(name = "modules", length = 1000)
    private String modules;

    @Column(name = "version_min", length = 20)
    private String versionMin;

    @Column(name = "version_max", length = 20)
    private String versionMax;

    @Column(name = "empreinte_serveur", length = 120)
    private String empreinteServeur;

    /** Signature RSA (base64url) de la charge utile. */
    @Column(name = "signature", columnDefinition = "TEXT")
    private String signature;

    /** Charge utile JSON signée, conservée telle quelle (re-vérifiable). */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "statut", nullable = false, length = 20)
    private String statut = "ACTIVE";

    /** Anti-recul d'horloge : plus grande date « vue » par l'application. */
    @Column(name = "derniere_date_vue")
    private LocalDateTime derniereDateVue;

    @Column(name = "importee_le", nullable = false)
    private LocalDateTime importeeLe;

    @PrePersist
    public void prePersist() { if (importeeLe == null) { importeeLe = LocalDateTime.now(); } }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getSociete() { return societe; }
    public void setSociete(String societe) { this.societe = societe; }
    public String getPays() { return pays; }
    public void setPays(String pays) { this.pays = pays; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public LocalDate getDateActivation() { return dateActivation; }
    public void setDateActivation(LocalDate dateActivation) { this.dateActivation = dateActivation; }
    public LocalDate getDateExpiration() { return dateExpiration; }
    public void setDateExpiration(LocalDate dateExpiration) { this.dateExpiration = dateExpiration; }
    public Integer getMaxUsers() { return maxUsers; }
    public void setMaxUsers(Integer maxUsers) { this.maxUsers = maxUsers; }
    public Integer getMaxAgences() { return maxAgences; }
    public void setMaxAgences(Integer maxAgences) { this.maxAgences = maxAgences; }
    public String getModules() { return modules; }
    public void setModules(String modules) { this.modules = modules; }
    public String getVersionMin() { return versionMin; }
    public void setVersionMin(String versionMin) { this.versionMin = versionMin; }
    public String getVersionMax() { return versionMax; }
    public void setVersionMax(String versionMax) { this.versionMax = versionMax; }
    public String getEmpreinteServeur() { return empreinteServeur; }
    public void setEmpreinteServeur(String empreinteServeur) { this.empreinteServeur = empreinteServeur; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public LocalDateTime getDerniereDateVue() { return derniereDateVue; }
    public void setDerniereDateVue(LocalDateTime derniereDateVue) { this.derniereDateVue = derniereDateVue; }
    public LocalDateTime getImporteeLe() { return importeeLe; }
    public void setImporteeLe(LocalDateTime importeeLe) { this.importeeLe = importeeLe; }
}
