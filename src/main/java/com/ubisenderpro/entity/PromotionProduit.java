package com.ubisenderpro.entity;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Produit rattaché à une promotion avec ses conditions d'unités gratuites (UG).
 * Au moins l'un de {taux_ug, quantite_ug} doit être renseigné.
 */
@Entity
@Table(name = "usp_promotion_produit")
public class PromotionProduit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "promotion_id", nullable = false)
    private Long promotionId;

    /** Article du catalogue rattaché (résolu via CIP7=cip / CIP13=code-barres), si trouvé. */
    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "cip7", length = 20)
    private String cip7;

    @Column(name = "cip13", length = 20)
    private String cip13;

    @Column(name = "nom_produit", length = 255)
    private String nomProduit;

    @Column(name = "quantite_minimale")
    private Integer quantiteMinimale;

    @Column(name = "taux_ug", precision = 7, scale = 2)
    private BigDecimal tauxUg;

    @Column(name = "taux_max_ug", precision = 7, scale = 2)
    private BigDecimal tauxMaxUg;

    @Column(name = "quantite_ug")
    private Integer quantiteUg;

    @Column(name = "quantite_ug_max")
    private Integer quantiteUgMax;

    /** TAUX | QUANTITE | MIXTE. */
    @Column(name = "mode_calcul", length = 20)
    private String modeCalcul;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPromotionId() { return promotionId; }
    public void setPromotionId(Long promotionId) { this.promotionId = promotionId; }
    public Long getArticleId() { return articleId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public String getCip7() { return cip7; }
    public void setCip7(String cip7) { this.cip7 = cip7; }
    public String getCip13() { return cip13; }
    public void setCip13(String cip13) { this.cip13 = cip13; }
    public String getNomProduit() { return nomProduit; }
    public void setNomProduit(String nomProduit) { this.nomProduit = nomProduit; }
    public Integer getQuantiteMinimale() { return quantiteMinimale; }
    public void setQuantiteMinimale(Integer quantiteMinimale) { this.quantiteMinimale = quantiteMinimale; }
    public BigDecimal getTauxUg() { return tauxUg; }
    public void setTauxUg(BigDecimal tauxUg) { this.tauxUg = tauxUg; }
    public BigDecimal getTauxMaxUg() { return tauxMaxUg; }
    public void setTauxMaxUg(BigDecimal tauxMaxUg) { this.tauxMaxUg = tauxMaxUg; }
    public Integer getQuantiteUg() { return quantiteUg; }
    public void setQuantiteUg(Integer quantiteUg) { this.quantiteUg = quantiteUg; }
    public Integer getQuantiteUgMax() { return quantiteUgMax; }
    public void setQuantiteUgMax(Integer quantiteUgMax) { this.quantiteUgMax = quantiteUgMax; }
    public String getModeCalcul() { return modeCalcul; }
    public void setModeCalcul(String modeCalcul) { this.modeCalcul = modeCalcul; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
