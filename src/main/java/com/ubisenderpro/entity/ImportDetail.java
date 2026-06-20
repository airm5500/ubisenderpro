package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usp_import_detail")
public class ImportDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "import_id", nullable = false)
    private Long importId;

    @Column(name = "numero_ligne", nullable = false)
    private int numeroLigne;

    @Column(name = "statut", nullable = false, length = 30)
    private String statut;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "contenu_ligne", columnDefinition = "TEXT")
    private String contenuLigne;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }
    public int getNumeroLigne() { return numeroLigne; }
    public void setNumeroLigne(int numeroLigne) { this.numeroLigne = numeroLigne; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getContenuLigne() { return contenuLigne; }
    public void setContenuLigne(String contenuLigne) { this.contenuLigne = contenuLigne; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
