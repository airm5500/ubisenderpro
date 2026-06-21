package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Travail d'envoi en masse via WhatsApp Web : jusqu'à 5 variantes de message
 * (rotation anti-spam), pièce jointe optionnelle et réglages de débit (anti-ban).
 */
@Entity
@Table(name = "usp_wa_bulk_job")
public class WaBulkJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "nom", length = 150)
    private String nom;

    @Column(name = "msg1", columnDefinition = "TEXT")
    private String msg1;
    @Column(name = "msg2", columnDefinition = "TEXT")
    private String msg2;
    @Column(name = "msg3", columnDefinition = "TEXT")
    private String msg3;
    @Column(name = "msg4", columnDefinition = "TEXT")
    private String msg4;
    @Column(name = "msg5", columnDefinition = "TEXT")
    private String msg5;

    /** URL publique de la pièce jointe (servie par /api/v1/media/{id}). */
    @Column(name = "media_url", length = 1000)
    private String mediaUrl;

    /** image | video | document | audio (null = pas de média) */
    @Column(name = "media_type", length = 20)
    private String mediaType;

    @Column(name = "media_mime", length = 100)
    private String mediaMime;

    @Column(name = "media_nom", length = 255)
    private String mediaNom;

    // Réglages de débit (secondes).
    @Column(name = "attente_min", nullable = false)
    private int attenteMin = 4;
    @Column(name = "attente_max", nullable = false)
    private int attenteMax = 8;
    @Column(name = "pause_apres", nullable = false)
    private int pauseApres = 10;
    @Column(name = "pause_min", nullable = false)
    private int pauseMin = 10;
    @Column(name = "pause_max", nullable = false)
    private int pauseMax = 20;

    /** BROUILLON | PLANIFIEE | EN_COURS | TERMINEE | SUSPENDUE */
    @Column(name = "statut", nullable = false, length = 20)
    private String statut = "BROUILLON";

    /** Date/heure de démarrage planifié (null = immédiat). */
    @Column(name = "date_programmee")
    private java.time.LocalDateTime dateProgrammee;

    /** Plage horaire autorisée [heureDebut, heureFin[ ; 0/0 = pas de restriction. */
    @Column(name = "heure_debut", nullable = false)
    private int heureDebut = 0;
    @Column(name = "heure_fin", nullable = false)
    private int heureFin = 0;

    @Column(name = "total", nullable = false)
    private int total = 0;
    @Column(name = "envoyes", nullable = false)
    private int envoyes = 0;
    @Column(name = "echoues", nullable = false)
    private int echoues = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "cree_par")
    private Long creePar;

    /** Motif d'échec représentatif (rempli à la lecture de la liste ; non persisté). */
    @Transient
    private String derniereErreur;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public String getDerniereErreur() { return derniereErreur; }
    public void setDerniereErreur(String derniereErreur) { this.derniereErreur = derniereErreur; }
    public Long getCreePar() { return creePar; }
    public void setCreePar(Long creePar) { this.creePar = creePar; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getMsg1() { return msg1; }
    public void setMsg1(String msg1) { this.msg1 = msg1; }
    public String getMsg2() { return msg2; }
    public void setMsg2(String msg2) { this.msg2 = msg2; }
    public String getMsg3() { return msg3; }
    public void setMsg3(String msg3) { this.msg3 = msg3; }
    public String getMsg4() { return msg4; }
    public void setMsg4(String msg4) { this.msg4 = msg4; }
    public String getMsg5() { return msg5; }
    public void setMsg5(String msg5) { this.msg5 = msg5; }
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public String getMediaMime() { return mediaMime; }
    public void setMediaMime(String mediaMime) { this.mediaMime = mediaMime; }
    public String getMediaNom() { return mediaNom; }
    public void setMediaNom(String mediaNom) { this.mediaNom = mediaNom; }
    public int getAttenteMin() { return attenteMin; }
    public void setAttenteMin(int attenteMin) { this.attenteMin = attenteMin; }
    public int getAttenteMax() { return attenteMax; }
    public void setAttenteMax(int attenteMax) { this.attenteMax = attenteMax; }
    public int getPauseApres() { return pauseApres; }
    public void setPauseApres(int pauseApres) { this.pauseApres = pauseApres; }
    public int getPauseMin() { return pauseMin; }
    public void setPauseMin(int pauseMin) { this.pauseMin = pauseMin; }
    public int getPauseMax() { return pauseMax; }
    public void setPauseMax(int pauseMax) { this.pauseMax = pauseMax; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public java.time.LocalDateTime getDateProgrammee() { return dateProgrammee; }
    public void setDateProgrammee(java.time.LocalDateTime dateProgrammee) { this.dateProgrammee = dateProgrammee; }
    public int getHeureDebut() { return heureDebut; }
    public void setHeureDebut(int heureDebut) { this.heureDebut = heureDebut; }
    public int getHeureFin() { return heureFin; }
    public void setHeureFin(int heureFin) { this.heureFin = heureFin; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public int getEnvoyes() { return envoyes; }
    public void setEnvoyes(int envoyes) { this.envoyes = envoyes; }
    public int getEchoues() { return echoues; }
    public void setEchoues(int echoues) { this.echoues = echoues; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
