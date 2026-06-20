package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usp_journal_action")
public class JournalAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "utilisateur_id")
    private Long utilisateurId;

    @Column(name = "login", length = 100)
    private String login;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "entite", length = 100)
    private String entite;

    @Column(name = "entite_id")
    private Long entiteId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "adresse_ip", length = 60)
    private String adresseIp;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUtilisateurId() { return utilisateurId; }
    public void setUtilisateurId(Long utilisateurId) { this.utilisateurId = utilisateurId; }
    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEntite() { return entite; }
    public void setEntite(String entite) { this.entite = entite; }
    public Long getEntiteId() { return entiteId; }
    public void setEntiteId(Long entiteId) { this.entiteId = entiteId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getAdresseIp() { return adresseIp; }
    public void setAdresseIp(String adresseIp) { this.adresseIp = adresseIp; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
