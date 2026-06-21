package com.ubisenderpro.dto;

import java.time.LocalDateTime;

/**
 * Ligne unifiée de l'historique global des envois (discussions, campagnes,
 * envois de masse WhatsApp Web). Vue lecture seule agrégée.
 */
public class HistoriqueLigne {

    /** DISCUSSION | CAMPAGNE | ENVOI_MASSE */
    private String type;
    /** API | WEB */
    private String canal;
    /** Référence d'origine (id du message, du destinataire de campagne ou de masse). */
    private Long sourceId;
    /** Id du regroupement parent (campagne ou job de masse), null pour les discussions. */
    private Long parentId;
    /** Libellé du regroupement (nom de campagne / d'envoi de masse). */
    private String libelle;
    private String numero;
    private String nom;
    /** Utilisateur émetteur (discussions) ; vide si non tracé (campagnes / envois de masse). */
    private String utilisateur;
    private String apercu;
    private String statut;
    private String erreur;
    private LocalDateTime date;

    public HistoriqueLigne() { }

    public HistoriqueLigne(String type, String canal, Long sourceId, Long parentId, String libelle,
                           String numero, String nom, String utilisateur, String apercu, String statut,
                           String erreur, LocalDateTime date) {
        this.type = type; this.canal = canal; this.sourceId = sourceId; this.parentId = parentId;
        this.libelle = libelle; this.numero = numero; this.nom = nom; this.utilisateur = utilisateur;
        this.apercu = apercu; this.statut = statut; this.erreur = erreur; this.date = date;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getUtilisateur() { return utilisateur; }
    public void setUtilisateur(String utilisateur) { this.utilisateur = utilisateur; }
    public String getApercu() { return apercu; }
    public void setApercu(String apercu) { this.apercu = apercu; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getErreur() { return erreur; }
    public void setErreur(String erreur) { this.erreur = erreur; }
    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }
}
