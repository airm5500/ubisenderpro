package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Historique des connexions/déconnexions des utilisateurs :
 * date/heure de connexion et de déconnexion, IP, poste, lieu, temps de travail.
 */
@Entity
@Table(name = "usp_connexion_log")
public class ConnexionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "utilisateur_id")
    private Long utilisateurId;

    @Column(name = "login", length = 100)
    private String login;

    @Column(name = "session_token", length = 120)
    private String sessionToken;

    @Column(name = "ip", length = 60)
    private String ip;

    /** Nom du poste (résolution inverse de l'IP si disponible). */
    @Column(name = "poste", length = 255)
    private String poste;

    /** Lieu approximatif (géolocalisation IP, best-effort). */
    @Column(name = "lieu", length = 255)
    private String lieu;

    @Column(name = "connexion_at", nullable = false)
    private LocalDateTime connexionAt;

    @Column(name = "deconnexion_at")
    private LocalDateTime deconnexionAt;

    @Column(name = "duree_secondes")
    private Long dureeSecondes;

    @PrePersist
    public void prePersist() { if (connexionAt == null) connexionAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUtilisateurId() { return utilisateurId; }
    public void setUtilisateurId(Long utilisateurId) { this.utilisateurId = utilisateurId; }
    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public String getPoste() { return poste; }
    public void setPoste(String poste) { this.poste = poste; }
    public String getLieu() { return lieu; }
    public void setLieu(String lieu) { this.lieu = lieu; }
    public LocalDateTime getConnexionAt() { return connexionAt; }
    public void setConnexionAt(LocalDateTime connexionAt) { this.connexionAt = connexionAt; }
    public LocalDateTime getDeconnexionAt() { return deconnexionAt; }
    public void setDeconnexionAt(LocalDateTime deconnexionAt) { this.deconnexionAt = deconnexionAt; }
    public Long getDureeSecondes() { return dureeSecondes; }
    public void setDureeSecondes(Long dureeSecondes) { this.dureeSecondes = dureeSecondes; }
}
