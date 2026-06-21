package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Fichier média importé et stocké en base (table usp_media_fichier).
 * Servi par une URL publique (GET /api/v1/media/{id}) afin que WhatsApp
 * puisse récupérer le fichier lors d'un envoi de média par lien.
 */
@Entity
@Table(name = "usp_media_fichier")
public class MediaFichier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom_fichier", length = 255)
    private String nomFichier;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "taille")
    private Long taille;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "contenu", nullable = false)
    private byte[] contenu;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNomFichier() { return nomFichier; }
    public void setNomFichier(String nomFichier) { this.nomFichier = nomFichier; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Long getTaille() { return taille; }
    public void setTaille(Long taille) { this.taille = taille; }
    public byte[] getContenu() { return contenu; }
    public void setContenu(byte[] contenu) { this.contenu = contenu; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
