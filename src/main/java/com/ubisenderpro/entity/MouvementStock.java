package com.ubisenderpro.entity;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "usp_mouvement_stock")
public class MouvementStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(name = "type_mouvement", nullable = false, length = 30)
    private String typeMouvement;

    @Column(name = "quantite_avant", precision = 15, scale = 3)
    private BigDecimal quantiteAvant;

    @Column(name = "quantite_mouvement", precision = 15, scale = 3)
    private BigDecimal quantiteMouvement;

    @Column(name = "quantite_apres", precision = 15, scale = 3)
    private BigDecimal quantiteApres;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "reference_source", length = 100)
    private String referenceSource;

    @Column(name = "commentaire", length = 500)
    private String commentaire;

    @Column(name = "utilisateur_id")
    private Long utilisateurId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getArticleId() { return articleId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public String getTypeMouvement() { return typeMouvement; }
    public void setTypeMouvement(String typeMouvement) { this.typeMouvement = typeMouvement; }
    public BigDecimal getQuantiteAvant() { return quantiteAvant; }
    public void setQuantiteAvant(BigDecimal quantiteAvant) { this.quantiteAvant = quantiteAvant; }
    public BigDecimal getQuantiteMouvement() { return quantiteMouvement; }
    public void setQuantiteMouvement(BigDecimal quantiteMouvement) { this.quantiteMouvement = quantiteMouvement; }
    public BigDecimal getQuantiteApres() { return quantiteApres; }
    public void setQuantiteApres(BigDecimal quantiteApres) { this.quantiteApres = quantiteApres; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getReferenceSource() { return referenceSource; }
    public void setReferenceSource(String referenceSource) { this.referenceSource = referenceSource; }
    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
    public Long getUtilisateurId() { return utilisateurId; }
    public void setUtilisateurId(Long utilisateurId) { this.utilisateurId = utilisateurId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
