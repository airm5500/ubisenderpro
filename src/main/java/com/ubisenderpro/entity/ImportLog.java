package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usp_import")
public class ImportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom_fichier")
    private String nomFichier;

    @Column(name = "type_import", nullable = false, length = 50)
    private String typeImport;

    @Column(name = "utilisateur_id")
    private Long utilisateurId;

    @Column(name = "mode_import", length = 50)
    private String modeImport;

    @Column(name = "nb_lignes", nullable = false)
    private int nbLignes = 0;

    @Column(name = "nb_crees", nullable = false)
    private int nbCrees = 0;

    @Column(name = "nb_mis_a_jour", nullable = false)
    private int nbMisAJour = 0;

    @Column(name = "nb_ignores", nullable = false)
    private int nbIgnores = 0;

    @Column(name = "nb_rejetes", nullable = false)
    private int nbRejetes = 0;

    @Column(name = "duree_ms")
    private Long dureeMs;

    @Column(name = "statut", nullable = false, length = 30)
    private String statut = "EN_COURS";

    @Column(name = "fichier_erreurs", columnDefinition = "LONGTEXT")
    private String fichierErreurs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNomFichier() { return nomFichier; }
    public void setNomFichier(String nomFichier) { this.nomFichier = nomFichier; }
    public String getTypeImport() { return typeImport; }
    public void setTypeImport(String typeImport) { this.typeImport = typeImport; }
    public Long getUtilisateurId() { return utilisateurId; }
    public void setUtilisateurId(Long utilisateurId) { this.utilisateurId = utilisateurId; }
    public String getModeImport() { return modeImport; }
    public void setModeImport(String modeImport) { this.modeImport = modeImport; }
    public int getNbLignes() { return nbLignes; }
    public void setNbLignes(int nbLignes) { this.nbLignes = nbLignes; }
    public int getNbCrees() { return nbCrees; }
    public void setNbCrees(int nbCrees) { this.nbCrees = nbCrees; }
    public int getNbMisAJour() { return nbMisAJour; }
    public void setNbMisAJour(int nbMisAJour) { this.nbMisAJour = nbMisAJour; }
    public int getNbIgnores() { return nbIgnores; }
    public void setNbIgnores(int nbIgnores) { this.nbIgnores = nbIgnores; }
    public int getNbRejetes() { return nbRejetes; }
    public void setNbRejetes(int nbRejetes) { this.nbRejetes = nbRejetes; }
    public Long getDureeMs() { return dureeMs; }
    public void setDureeMs(Long dureeMs) { this.dureeMs = dureeMs; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getFichierErreurs() { return fichierErreurs; }
    public void setFichierErreurs(String fichierErreurs) { this.fichierErreurs = fichierErreurs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
