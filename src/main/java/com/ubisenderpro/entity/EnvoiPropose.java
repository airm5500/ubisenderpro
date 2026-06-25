package com.ubisenderpro.entity;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Proposition d'envoi générée automatiquement à partir des promotions
 * (annonce mensuelle, lancement, rappels J-7 / J-3 / J-1).
 *
 * <p>Une proposition n'envoie rien : elle doit être <b>validée par un opérateur</b>
 * pour devenir une campagne (en brouillon). C'est le point de contrôle humain.</p>
 */
@Entity
@Table(name = "usp_envoi_propose")
public class EnvoiPropose {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Clé d'idempotence (type + promotion + période) : unicité garantie en base. */
    @Column(name = "cle", nullable = false, unique = true, length = 120)
    private String cle;

    /** ANNONCE_MENSUELLE | LANCEMENT | RAPPEL_J7 | RAPPEL_J3 | RAPPEL_J1 */
    @Column(name = "type", nullable = false, length = 30)
    private String type;

    /** Source de la proposition : PROMO (promotion) | DISPO (événement disponibilité/rupture). */
    @Column(name = "source", length = 10)
    private String source = "PROMO";

    @Column(name = "promotion_id")
    private Long promotionId;

    /** Événement dispo/rupture source (si source = DISPO). */
    @Column(name = "evenement_id")
    private Long evenementId;

    @Column(name = "titre", nullable = false, length = 200)
    private String titre;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "date_prevue", nullable = false)
    private LocalDate datePrevue;

    /** PROPOSEE | VALIDEE | REJETEE | EXPIREE */
    @Column(name = "statut", nullable = false, length = 20)
    private String statut = "PROPOSEE";

    @Column(name = "campagne_id")
    private Long campagneId;

    @Column(name = "liste_id")
    private Long listeId;

    @Column(name = "segment_id")
    private Long segmentId;

    @Column(name = "motif_rejet", length = 255)
    private String motifRejet;

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
    public String getCle() { return cle; }
    public void setCle(String cle) { this.cle = cle; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getPromotionId() { return promotionId; }
    public void setPromotionId(Long promotionId) { this.promotionId = promotionId; }
    public Long getEvenementId() { return evenementId; }
    public void setEvenementId(Long evenementId) { this.evenementId = evenementId; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDate getDatePrevue() { return datePrevue; }
    public void setDatePrevue(LocalDate datePrevue) { this.datePrevue = datePrevue; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public Long getCampagneId() { return campagneId; }
    public void setCampagneId(Long campagneId) { this.campagneId = campagneId; }
    public Long getListeId() { return listeId; }
    public void setListeId(Long listeId) { this.listeId = listeId; }
    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }
    public String getMotifRejet() { return motifRejet; }
    public void setMotifRejet(String motifRejet) { this.motifRejet = motifRejet; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
