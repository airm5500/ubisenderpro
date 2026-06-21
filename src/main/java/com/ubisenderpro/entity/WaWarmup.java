package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Plan de « réchauffe » (warming) d'une session WhatsApp Web : envoi progressif
 * de petits messages neutres vers un pool de numéros, pour bâtir une réputation
 * d'usage normal et limiter le risque de bannissement avant les gros volumes.
 */
@Entity
@Table(name = "usp_wa_warmup")
public class WaWarmup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true)
    private Long sessionId;

    @Column(name = "actif", nullable = false)
    private boolean actif = false;

    /** Pool de numéros (un par ligne), idéalement vos propres numéros. */
    @Column(name = "numeros", columnDefinition = "TEXT")
    private String numeros;

    @Column(name = "par_jour_base", nullable = false)
    private int parJourBase = 10;
    @Column(name = "par_jour_max", nullable = false)
    private int parJourMax = 60;
    @Column(name = "increment_jour", nullable = false)
    private int incrementJour = 10;

    @Column(name = "jour_courant", nullable = false)
    private int jourCourant = 1;
    @Column(name = "envoyes_jour", nullable = false)
    private int envoyesJour = 0;
    @Column(name = "date_jour")
    private LocalDate dateJour;
    @Column(name = "dernier_envoi")
    private LocalDateTime dernierEnvoi;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public String getNumeros() { return numeros; }
    public void setNumeros(String numeros) { this.numeros = numeros; }
    public int getParJourBase() { return parJourBase; }
    public void setParJourBase(int parJourBase) { this.parJourBase = parJourBase; }
    public int getParJourMax() { return parJourMax; }
    public void setParJourMax(int parJourMax) { this.parJourMax = parJourMax; }
    public int getIncrementJour() { return incrementJour; }
    public void setIncrementJour(int incrementJour) { this.incrementJour = incrementJour; }
    public int getJourCourant() { return jourCourant; }
    public void setJourCourant(int jourCourant) { this.jourCourant = jourCourant; }
    public int getEnvoyesJour() { return envoyesJour; }
    public void setEnvoyesJour(int envoyesJour) { this.envoyesJour = envoyesJour; }
    public LocalDate getDateJour() { return dateJour; }
    public void setDateJour(LocalDate dateJour) { this.dateJour = dateJour; }
    public LocalDateTime getDernierEnvoi() { return dernierEnvoi; }
    public void setDernierEnvoi(LocalDateTime dernierEnvoi) { this.dernierEnvoi = dernierEnvoi; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
