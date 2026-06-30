package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Communication d'information / alerte opérationnelle (module Informations Clients).
 * Un seul écran : les champs « livraison » (§8) et « garde » (§9) sont optionnels
 * et affichés selon le type. Alimente l'agrégateur Marketing (source = INFO).
 */
@Entity
@Table(name = "usp_info_evenement")
public class InfoEvenement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "type", nullable = false, length = 40)
    private String type;

    @Column(name = "titre", nullable = false, length = 200)
    private String titre;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    /** NORMALE | IMPORTANTE | URGENTE | CRITIQUE. */
    @Column(name = "priorite", length = 20)
    private String priorite = "NORMALE";

    @Column(name = "societe", length = 150)
    private String societe;

    @Column(name = "agence", length = 150)
    private String agence;

    @Column(name = "region", length = 150)
    private String region;

    @Column(name = "tournee", length = 150)
    private String tournee;

    @Column(name = "audience", length = 40)
    private String audience;

    @Column(name = "segmentation_id")
    private Long segmentationId;

    @Column(name = "liste_id")
    private Long listeId;

    /** Sélection manuelle de contacts (IDs séparés par des virgules). */
    @Column(name = "contact_ids", length = 2000)
    private String contactIds;

    @Column(name = "canal", length = 10)
    private String canal;

    @Column(name = "modele_id")
    private Long modeleId;

    @Column(name = "date_envoi")
    private LocalDateTime dateEnvoi;

    @Column(name = "date_fin_validite")
    private LocalDateTime dateFinValidite;

    /** BROUILLON | EN_ATTENTE | PROGRAMMEE | EN_COURS | ENVOYEE | ANNULEE | EXPIREE | ECHOUEE | ARCHIVEE. */
    @Column(name = "statut", length = 20)
    private String statut;

    @Column(name = "responsable", length = 150)
    private String responsable;

    @Column(name = "cree_par", length = 100)
    private String creePar;

    /* ---- Détails livraison (§8) ---- */
    @Column(name = "date_livraison")
    private LocalDate dateLivraison;
    @Column(name = "creneau", length = 100)
    private String creneau;
    @Column(name = "heure_initiale", length = 20)
    private String heureInitiale;
    @Column(name = "nouvelle_heure", length = 20)
    private String nouvelleHeure;
    @Column(name = "cause_interne", length = 255)
    private String causeInterne;
    @Column(name = "cause_communicable", length = 255)
    private String causeCommunicable;
    @Column(name = "date_resolution")
    private LocalDate dateResolution;

    /* ---- Détails garde / jour férié (§9) ---- */
    @Column(name = "jour_ferie", length = 150)
    private String jourFerie;
    @Column(name = "date_garde")
    private LocalDate dateGarde;
    @Column(name = "heure_limite_commande", length = 20)
    private String heureLimiteCommande;
    @Column(name = "consignes_livraison", length = 500)
    private String consignesLivraison;
    @Column(name = "pharmacien_garde", length = 150)
    private String pharmacienGarde;
    @Column(name = "telephone_pharmacien", length = 40)
    private String telephonePharmacien;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

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
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getPriorite() { return priorite; }
    public void setPriorite(String priorite) { this.priorite = priorite; }
    public String getSociete() { return societe; }
    public void setSociete(String societe) { this.societe = societe; }
    public String getAgence() { return agence; }
    public void setAgence(String agence) { this.agence = agence; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getTournee() { return tournee; }
    public void setTournee(String tournee) { this.tournee = tournee; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public Long getSegmentationId() { return segmentationId; }
    public void setSegmentationId(Long segmentationId) { this.segmentationId = segmentationId; }
    public Long getListeId() { return listeId; }
    public void setListeId(Long listeId) { this.listeId = listeId; }
    public String getContactIds() { return contactIds; }
    public void setContactIds(String contactIds) { this.contactIds = contactIds; }
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public Long getModeleId() { return modeleId; }
    public void setModeleId(Long modeleId) { this.modeleId = modeleId; }
    public LocalDateTime getDateEnvoi() { return dateEnvoi; }
    public void setDateEnvoi(LocalDateTime dateEnvoi) { this.dateEnvoi = dateEnvoi; }
    public LocalDateTime getDateFinValidite() { return dateFinValidite; }
    public void setDateFinValidite(LocalDateTime dateFinValidite) { this.dateFinValidite = dateFinValidite; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getResponsable() { return responsable; }
    public void setResponsable(String responsable) { this.responsable = responsable; }
    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
    public LocalDate getDateLivraison() { return dateLivraison; }
    public void setDateLivraison(LocalDate dateLivraison) { this.dateLivraison = dateLivraison; }
    public String getCreneau() { return creneau; }
    public void setCreneau(String creneau) { this.creneau = creneau; }
    public String getHeureInitiale() { return heureInitiale; }
    public void setHeureInitiale(String heureInitiale) { this.heureInitiale = heureInitiale; }
    public String getNouvelleHeure() { return nouvelleHeure; }
    public void setNouvelleHeure(String nouvelleHeure) { this.nouvelleHeure = nouvelleHeure; }
    public String getCauseInterne() { return causeInterne; }
    public void setCauseInterne(String causeInterne) { this.causeInterne = causeInterne; }
    public String getCauseCommunicable() { return causeCommunicable; }
    public void setCauseCommunicable(String causeCommunicable) { this.causeCommunicable = causeCommunicable; }
    public LocalDate getDateResolution() { return dateResolution; }
    public void setDateResolution(LocalDate dateResolution) { this.dateResolution = dateResolution; }
    public String getJourFerie() { return jourFerie; }
    public void setJourFerie(String jourFerie) { this.jourFerie = jourFerie; }
    public LocalDate getDateGarde() { return dateGarde; }
    public void setDateGarde(LocalDate dateGarde) { this.dateGarde = dateGarde; }
    public String getHeureLimiteCommande() { return heureLimiteCommande; }
    public void setHeureLimiteCommande(String heureLimiteCommande) { this.heureLimiteCommande = heureLimiteCommande; }
    public String getConsignesLivraison() { return consignesLivraison; }
    public void setConsignesLivraison(String consignesLivraison) { this.consignesLivraison = consignesLivraison; }
    public String getPharmacienGarde() { return pharmacienGarde; }
    public void setPharmacienGarde(String pharmacienGarde) { this.pharmacienGarde = pharmacienGarde; }
    public String getTelephonePharmacien() { return telephonePharmacien; }
    public void setTelephonePharmacien(String telephonePharmacien) { this.telephonePharmacien = telephonePharmacien; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
