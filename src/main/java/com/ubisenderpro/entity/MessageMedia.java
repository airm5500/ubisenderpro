package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Média rattaché à un message WhatsApp (table usp_message_media).
 * Conserve l'identifiant Meta (wa_media_id) obtenu lors du téléversement
 * afin de tracer et, à terme, réafficher les pièces jointes.
 */
@Entity
@Table(name = "usp_message_media")
public class MessageMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "type_media", nullable = false, length = 20)
    private String typeMedia;

    @Column(name = "wa_media_id", length = 150)
    private String waMediaId;

    @Column(name = "url", length = 1000)
    private String url;

    @Column(name = "chemin_local", length = 500)
    private String cheminLocal;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "nom_fichier", length = 255)
    private String nomFichier;

    @Column(name = "taille")
    private Long taille;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public String getTypeMedia() { return typeMedia; }
    public void setTypeMedia(String typeMedia) { this.typeMedia = typeMedia; }
    public String getWaMediaId() { return waMediaId; }
    public void setWaMediaId(String waMediaId) { this.waMediaId = waMediaId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getCheminLocal() { return cheminLocal; }
    public void setCheminLocal(String cheminLocal) { this.cheminLocal = cheminLocal; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getNomFichier() { return nomFichier; }
    public void setNomFichier(String nomFichier) { this.nomFichier = nomFichier; }
    public Long getTaille() { return taille; }
    public void setTaille(Long taille) { this.taille = taille; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
