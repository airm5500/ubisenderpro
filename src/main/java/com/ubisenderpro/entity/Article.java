package com.ubisenderpro.entity;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "usp_article")
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pscode", nullable = false, unique = true, length = 50)
    private String pscode;

    @Column(name = "code_barres", length = 50)
    private String codeBarres;

    @Column(name = "cip", length = 50)
    private String cip;

    @Column(name = "designation", nullable = false)
    private String designation;

    @Column(name = "description_courte", length = 500)
    private String descriptionCourte;

    @Column(name = "description_complete", columnDefinition = "TEXT")
    private String descriptionComplete;

    @Column(name = "categorie_id")
    private Long categorieId;

    @Column(name = "marque_id")
    private Long marqueId;

    @Column(name = "prix_vente", nullable = false, precision = 15, scale = 2)
    private BigDecimal prixVente = BigDecimal.ZERO;

    @Column(name = "prix_vente_public", precision = 15, scale = 2)
    private BigDecimal prixVentePublic;

    @Column(name = "prix_promotionnel", precision = 15, scale = 2)
    private BigDecimal prixPromotionnel;

    @Column(name = "quantite_commandee")
    private Integer quantiteCommandee;

    /** Unités gratuites (UG) accordées pour la quantité commandée. */
    @Column(name = "quantite_ug")
    private Integer quantiteUg;

    @Column(name = "nom_promo", length = 150)
    private String nomPromo;

    @Column(name = "code_promo", length = 50)
    private String codePromo;

    /** Promotion associée (réf. usp_promotion) ; null = pas de promotion. (Hérité V22, conservé) */
    @Column(name = "promotion_id")
    private Long promotionId;

    /** Promotions associées (un article peut être dans plusieurs promotions). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "usp_article_promotion",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "promotion_id"))
    private java.util.Set<Promotion> promotions = new java.util.HashSet<>();

    /** Ids des promotions reçus du client (écriture) ; null = ne pas modifier les associations. */
    @Transient
    private java.util.List<Long> promotionIds;

    @Column(name = "date_debut_promotion")
    private LocalDateTime dateDebutPromotion;

    @Column(name = "date_fin_promotion")
    private LocalDateTime dateFinPromotion;

    @Column(name = "stock_disponible", nullable = false, precision = 15, scale = 3)
    private BigDecimal stockDisponible = BigDecimal.ZERO;

    @Column(name = "seuil_alerte", nullable = false, precision = 15, scale = 3)
    private BigDecimal seuilAlerte = BigDecimal.ZERO;

    @Column(name = "unite", length = 30)
    private String unite;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "image_locale", length = 500)
    private String imageLocale;

    @Column(name = "lien_produit", length = 1000)
    private String lienProduit;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "publiable", nullable = false)
    private boolean publiable = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPscode() { return pscode; }
    public void setPscode(String pscode) { this.pscode = pscode; }
    public String getCodeBarres() { return codeBarres; }
    public void setCodeBarres(String codeBarres) { this.codeBarres = codeBarres; }
    public String getCip() { return cip; }
    public void setCip(String cip) { this.cip = cip; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getDescriptionCourte() { return descriptionCourte; }
    public void setDescriptionCourte(String descriptionCourte) { this.descriptionCourte = descriptionCourte; }
    public String getDescriptionComplete() { return descriptionComplete; }
    public void setDescriptionComplete(String descriptionComplete) { this.descriptionComplete = descriptionComplete; }
    public Long getCategorieId() { return categorieId; }
    public void setCategorieId(Long categorieId) { this.categorieId = categorieId; }
    public Long getMarqueId() { return marqueId; }
    public void setMarqueId(Long marqueId) { this.marqueId = marqueId; }
    public BigDecimal getPrixVente() { return prixVente; }
    public void setPrixVente(BigDecimal prixVente) { this.prixVente = prixVente; }
    public BigDecimal getPrixVentePublic() { return prixVentePublic; }
    public void setPrixVentePublic(BigDecimal prixVentePublic) { this.prixVentePublic = prixVentePublic; }
    public BigDecimal getPrixPromotionnel() { return prixPromotionnel; }
    public void setPrixPromotionnel(BigDecimal prixPromotionnel) { this.prixPromotionnel = prixPromotionnel; }
    public Integer getQuantiteCommandee() { return quantiteCommandee; }
    public void setQuantiteCommandee(Integer quantiteCommandee) { this.quantiteCommandee = quantiteCommandee; }
    public Integer getQuantiteUg() { return quantiteUg; }
    public void setQuantiteUg(Integer quantiteUg) { this.quantiteUg = quantiteUg; }
    public String getNomPromo() { return nomPromo; }
    public void setNomPromo(String nomPromo) { this.nomPromo = nomPromo; }
    public String getCodePromo() { return codePromo; }
    public void setCodePromo(String codePromo) { this.codePromo = codePromo; }
    public Long getPromotionId() { return promotionId; }
    public void setPromotionId(Long promotionId) { this.promotionId = promotionId; }
    public java.util.Set<Promotion> getPromotions() { return promotions; }
    public void setPromotions(java.util.Set<Promotion> promotions) { this.promotions = promotions; }
    public java.util.List<Long> getPromotionIds() { return promotionIds; }
    public void setPromotionIds(java.util.List<Long> promotionIds) { this.promotionIds = promotionIds; }
    public LocalDateTime getDateDebutPromotion() { return dateDebutPromotion; }
    public void setDateDebutPromotion(LocalDateTime dateDebutPromotion) { this.dateDebutPromotion = dateDebutPromotion; }
    public LocalDateTime getDateFinPromotion() { return dateFinPromotion; }
    public void setDateFinPromotion(LocalDateTime dateFinPromotion) { this.dateFinPromotion = dateFinPromotion; }
    public BigDecimal getStockDisponible() { return stockDisponible; }
    public void setStockDisponible(BigDecimal stockDisponible) { this.stockDisponible = stockDisponible; }
    public BigDecimal getSeuilAlerte() { return seuilAlerte; }
    public void setSeuilAlerte(BigDecimal seuilAlerte) { this.seuilAlerte = seuilAlerte; }
    public String getUnite() { return unite; }
    public void setUnite(String unite) { this.unite = unite; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getImageLocale() { return imageLocale; }
    public void setImageLocale(String imageLocale) { this.imageLocale = imageLocale; }
    public String getLienProduit() { return lienProduit; }
    public void setLienProduit(String lienProduit) { this.lienProduit = lienProduit; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public boolean isPubliable() { return publiable; }
    public void setPubliable(boolean publiable) { this.publiable = publiable; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
