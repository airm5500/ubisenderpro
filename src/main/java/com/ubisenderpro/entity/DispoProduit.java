package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Produit concerné par un événement de disponibilité / rupture.
 */
@Entity
@Table(name = "usp_dispo_produit")
public class DispoProduit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evenement_id", nullable = false)
    private Long evenementId;

    /** Article du catalogue rattaché (résolu via CIP7=cip / CIP13=code-barres), si trouvé. */
    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "cip7", length = 20)
    private String cip7;

    @Column(name = "cip13", length = 20)
    private String cip13;

    @Column(name = "nom_produit", length = 255)
    private String nomProduit;

    @Column(name = "quantite_disponible")
    private Integer quantiteDisponible;

    @Column(name = "seuil_rupture")
    private Integer seuilRupture;

    @Column(name = "couverture_jours")
    private Integer couvertureJours;

    @Column(name = "date_peremption")
    private LocalDate datePeremption;

    @Column(name = "numero_lot", length = 50)
    private String numeroLot;

    @Column(name = "agence", length = 150)
    private String agence;

    @Column(name = "stock_limite", nullable = false)
    private boolean stockLimite = false;

    @Column(name = "lien_reservation", length = 500)
    private String lienReservation;

    /** DISPONIBLE | STOCK_LIMITE | RISQUE_RUPTURE | EN_RUPTURE | RETOUR_RUPTURE | INACTIF | ARCHIVE. */
    @Column(name = "statut", length = 20)
    private String statut;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEvenementId() { return evenementId; }
    public void setEvenementId(Long evenementId) { this.evenementId = evenementId; }
    public Long getArticleId() { return articleId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public String getCip7() { return cip7; }
    public void setCip7(String cip7) { this.cip7 = cip7; }
    public String getCip13() { return cip13; }
    public void setCip13(String cip13) { this.cip13 = cip13; }
    public String getNomProduit() { return nomProduit; }
    public void setNomProduit(String nomProduit) { this.nomProduit = nomProduit; }
    public Integer getQuantiteDisponible() { return quantiteDisponible; }
    public void setQuantiteDisponible(Integer quantiteDisponible) { this.quantiteDisponible = quantiteDisponible; }
    public Integer getSeuilRupture() { return seuilRupture; }
    public void setSeuilRupture(Integer seuilRupture) { this.seuilRupture = seuilRupture; }
    public Integer getCouvertureJours() { return couvertureJours; }
    public void setCouvertureJours(Integer couvertureJours) { this.couvertureJours = couvertureJours; }
    public LocalDate getDatePeremption() { return datePeremption; }
    public void setDatePeremption(LocalDate datePeremption) { this.datePeremption = datePeremption; }
    public String getNumeroLot() { return numeroLot; }
    public void setNumeroLot(String numeroLot) { this.numeroLot = numeroLot; }
    public String getAgence() { return agence; }
    public void setAgence(String agence) { this.agence = agence; }
    public boolean isStockLimite() { return stockLimite; }
    public void setStockLimite(boolean stockLimite) { this.stockLimite = stockLimite; }
    public String getLienReservation() { return lienReservation; }
    public void setLienReservation(String lienReservation) { this.lienReservation = lienReservation; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
