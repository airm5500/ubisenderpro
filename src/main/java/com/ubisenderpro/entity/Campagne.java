package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usp_campagne")
public class Campagne {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom", nullable = false, length = 150)
    private String nom;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "objectif", length = 255)
    private String objectif;

    @Column(name = "categorie", length = 60)
    private String categorie;

    @Column(name = "responsable_id")
    private Long responsableId;

    @Column(name = "whatsapp_account_id")
    private Long whatsappAccountId;

    @Column(name = "modele_id")
    private Long modeleId;

    @Column(name = "liste_id")
    private Long listeId;

    @Column(name = "segment_id")
    private Long segmentId;

    @Column(name = "segmentation_id")
    private Long segmentationId;

    /** Audience logique (§16) : TOUS_LES_SEGMENTS | DIAMOND | PLATINIUM | ... */
    @Column(name = "audience", length = 40)
    private String audience;

    /** Segmentations résolues à cibler (liste d'IDs séparés par des virgules). */
    @Column(name = "segmentation_ids", length = 255)
    private String segmentationIds;

    @Column(name = "statut", nullable = false, length = 20)
    private String statut = "BROUILLON";

    /** Canal d'envoi : 'API' (Cloud API officielle) ou 'WEB' (WhatsApp Web). */
    @Column(name = "canal", nullable = false, length = 10)
    private String canal = "API";

    /** Session WhatsApp Web utilisée lorsque {@link #canal} = 'WEB'. */
    @Column(name = "wa_web_session_id", length = 80)
    private String waWebSessionId;

    @Column(name = "date_programmee")
    private LocalDateTime dateProgrammee;

    @Column(name = "fuseau_horaire", length = 60)
    private String fuseauHoraire;

    @Column(name = "nb_destinataires", nullable = false)
    private int nbDestinataires = 0;

    @Column(name = "nb_envoyes", nullable = false)
    private int nbEnvoyes = 0;

    @Column(name = "nb_distribues", nullable = false)
    private int nbDistribues = 0;

    @Column(name = "nb_lus", nullable = false)
    private int nbLus = 0;

    @Column(name = "nb_repondus", nullable = false)
    private int nbRepondus = 0;

    @Column(name = "nb_echoues", nullable = false)
    private int nbEchoues = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "cree_par")
    private Long creePar;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getObjectif() { return objectif; }
    public void setObjectif(String objectif) { this.objectif = objectif; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public Long getResponsableId() { return responsableId; }
    public void setResponsableId(Long responsableId) { this.responsableId = responsableId; }
    public Long getWhatsappAccountId() { return whatsappAccountId; }
    public void setWhatsappAccountId(Long whatsappAccountId) { this.whatsappAccountId = whatsappAccountId; }
    public Long getModeleId() { return modeleId; }
    public void setModeleId(Long modeleId) { this.modeleId = modeleId; }
    public Long getListeId() { return listeId; }
    public void setListeId(Long listeId) { this.listeId = listeId; }
    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }
    public Long getSegmentationId() { return segmentationId; }
    public void setSegmentationId(Long segmentationId) { this.segmentationId = segmentationId; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public String getSegmentationIds() { return segmentationIds; }
    public void setSegmentationIds(String segmentationIds) { this.segmentationIds = segmentationIds; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public String getWaWebSessionId() { return waWebSessionId; }
    public void setWaWebSessionId(String waWebSessionId) { this.waWebSessionId = waWebSessionId; }
    public LocalDateTime getDateProgrammee() { return dateProgrammee; }
    public void setDateProgrammee(LocalDateTime dateProgrammee) { this.dateProgrammee = dateProgrammee; }
    public String getFuseauHoraire() { return fuseauHoraire; }
    public void setFuseauHoraire(String fuseauHoraire) { this.fuseauHoraire = fuseauHoraire; }
    public int getNbDestinataires() { return nbDestinataires; }
    public void setNbDestinataires(int nbDestinataires) { this.nbDestinataires = nbDestinataires; }
    public int getNbEnvoyes() { return nbEnvoyes; }
    public void setNbEnvoyes(int nbEnvoyes) { this.nbEnvoyes = nbEnvoyes; }
    public int getNbDistribues() { return nbDistribues; }
    public void setNbDistribues(int nbDistribues) { this.nbDistribues = nbDistribues; }
    public int getNbLus() { return nbLus; }
    public void setNbLus(int nbLus) { this.nbLus = nbLus; }
    public int getNbRepondus() { return nbRepondus; }
    public void setNbRepondus(int nbRepondus) { this.nbRepondus = nbRepondus; }
    public int getNbEchoues() { return nbEchoues; }
    public void setNbEchoues(int nbEchoues) { this.nbEchoues = nbEchoues; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getCreePar() { return creePar; }
    public void setCreePar(Long creePar) { this.creePar = creePar; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
