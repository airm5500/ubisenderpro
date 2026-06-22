package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Entrée de la base de connaissance du bot : des mots-clés déclencheurs
 * (séparés par des virgules) associés à une réponse automatique.
 */
@Entity
@Table(name = "usp_bot_faq")
public class BotFaq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "declencheurs", nullable = false, length = 1000)
    private String declencheurs;

    @Column(name = "reponse", nullable = false, columnDefinition = "TEXT")
    private String reponse;

    @Column(name = "ordre", nullable = false)
    private int ordre = 0;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDeclencheurs() { return declencheurs; }
    public void setDeclencheurs(String declencheurs) { this.declencheurs = declencheurs; }
    public String getReponse() { return reponse; }
    public void setReponse(String reponse) { this.reponse = reponse; }
    public int getOrdre() { return ordre; }
    public void setOrdre(int ordre) { this.ordre = ordre; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
