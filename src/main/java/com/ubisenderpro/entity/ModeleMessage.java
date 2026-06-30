package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usp_modele_message")
public class ModeleMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom", nullable = false, length = 150)
    private String nom;

    @Column(name = "type_modele", nullable = false, length = 40)
    private String typeModele;

    @Column(name = "langue", nullable = false, length = 10)
    private String langue = "fr";

    @Column(name = "categorie", length = 40)
    private String categorie;

    @Column(name = "entete_texte", length = 255)
    private String enteteTexte;

    @Column(name = "entete_media_type", length = 20)
    private String enteteMediaType;

    @Column(name = "entete_media_url", length = 1000)
    private String enteteMediaUrl;

    @Column(name = "corps", nullable = false, columnDefinition = "TEXT")
    private String corps;

    @Column(name = "pied_de_page", length = 255)
    private String piedDePage;

    @Column(name = "boutons_json", columnDefinition = "TEXT")
    private String boutonsJson;

    @Column(name = "nom_modele_whatsapp", length = 150)
    private String nomModeleWhatsapp;

    /** Segmentation client à laquelle ce modèle est dédié (null = tous). */
    @Column(name = "segmentation_id")
    private Long segmentationId;

    @Column(name = "statut_approbation", nullable = false, length = 30)
    private String statutApprobation = "BROUILLON";

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Rôle système d'un modèle prédéfini (ex. PROMO_LANCEMENT), null sinon. */
    @Column(name = "cle_systeme", length = 40)
    private String cleSysteme;

    /** Variables (CSV ordonné) injectées dans les paramètres {{1}},{{2}}… d'un template Meta. */
    @Column(name = "params_corps", length = 500)
    private String paramsCorps;

    /**
     * Variables de contexte de la campagne (JSON clé→valeur) figées à la validation :
     * mois_promotion, date_debut, date_fin, avantage_ug… Utilisées pour remplir les
     * paramètres d'un template Meta (canal API) qui ne sont pas résolus par contact.
     */
    @Column(name = "variables_contexte", columnDefinition = "TEXT")
    private String variablesContexte;

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getTypeModele() { return typeModele; }
    public void setTypeModele(String typeModele) { this.typeModele = typeModele; }
    public String getLangue() { return langue; }
    public void setLangue(String langue) { this.langue = langue; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public String getEnteteTexte() { return enteteTexte; }
    public void setEnteteTexte(String enteteTexte) { this.enteteTexte = enteteTexte; }
    public String getEnteteMediaType() { return enteteMediaType; }
    public void setEnteteMediaType(String enteteMediaType) { this.enteteMediaType = enteteMediaType; }
    public String getEnteteMediaUrl() { return enteteMediaUrl; }
    public void setEnteteMediaUrl(String enteteMediaUrl) { this.enteteMediaUrl = enteteMediaUrl; }
    public String getCorps() { return corps; }
    public void setCorps(String corps) { this.corps = corps; }
    public String getPiedDePage() { return piedDePage; }
    public void setPiedDePage(String piedDePage) { this.piedDePage = piedDePage; }
    public String getBoutonsJson() { return boutonsJson; }
    public void setBoutonsJson(String boutonsJson) { this.boutonsJson = boutonsJson; }
    public String getNomModeleWhatsapp() { return nomModeleWhatsapp; }
    public void setNomModeleWhatsapp(String nomModeleWhatsapp) { this.nomModeleWhatsapp = nomModeleWhatsapp; }
    public Long getSegmentationId() { return segmentationId; }
    public void setSegmentationId(Long segmentationId) { this.segmentationId = segmentationId; }
    public String getStatutApprobation() { return statutApprobation; }
    public void setStatutApprobation(String statutApprobation) { this.statutApprobation = statutApprobation; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCleSysteme() { return cleSysteme; }
    public void setCleSysteme(String cleSysteme) { this.cleSysteme = cleSysteme; }
    public String getParamsCorps() { return paramsCorps; }
    public void setParamsCorps(String paramsCorps) { this.paramsCorps = paramsCorps; }
    public String getVariablesContexte() { return variablesContexte; }
    public void setVariablesContexte(String variablesContexte) { this.variablesContexte = variablesContexte; }
}
