package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/** Demande « Me contacter » du Centre de support (archivée + e-mail à l'éditeur). */
@Entity
@Table(name = "usp_support_demande")
public class SupportDemande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom", length = 150)
    private String nom;

    @Column(name = "societe", length = 150)
    private String societe;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "telephone", length = 50)
    private String telephone;

    @Column(name = "objet", nullable = false)
    private String objet;

    @Column(name = "corps", nullable = false, columnDefinition = "TEXT")
    private String corps;

    /** Ids MediaFichier des pièces jointes, en CSV. */
    @Column(name = "pieces", length = 500)
    private String pieces;

    @Column(name = "statut", nullable = false, length = 20)
    private String statut = "ENVOYEE";

    @Column(name = "erreur", length = 500)
    private String erreur;

    @Column(name = "cree_par", length = 100)
    private String creePar;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { if (createdAt == null) { createdAt = LocalDateTime.now(); } }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getSociete() { return societe; }
    public void setSociete(String societe) { this.societe = societe; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getObjet() { return objet; }
    public void setObjet(String objet) { this.objet = objet; }
    public String getCorps() { return corps; }
    public void setCorps(String corps) { this.corps = corps; }
    public String getPieces() { return pieces; }
    public void setPieces(String pieces) { this.pieces = pieces; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getErreur() { return erreur; }
    public void setErreur(String erreur) { this.erreur = erreur; }
    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
