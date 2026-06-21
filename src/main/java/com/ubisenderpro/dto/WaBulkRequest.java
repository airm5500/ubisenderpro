package com.ubisenderpro.dto;

/**
 * Création d'un envoi en masse WhatsApp Web.
 * destinatairesTexte : une ligne par destinataire, format « numero;nom »
 * (le séparateur peut être ; , ou tabulation ; le nom est optionnel).
 */
public class WaBulkRequest {
    private Long sessionId;
    private String nom;
    private String msg1;
    private String msg2;
    private String msg3;
    private String msg4;
    private String msg5;
    private String mediaUrl;
    private String mediaType;
    private String mediaMime;
    private String mediaNom;
    private Integer attenteMin;
    private Integer attenteMax;
    private Integer pauseApres;
    private Integer pauseMin;
    private Integer pauseMax;
    private String destinatairesTexte;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getMsg1() { return msg1; }
    public void setMsg1(String msg1) { this.msg1 = msg1; }
    public String getMsg2() { return msg2; }
    public void setMsg2(String msg2) { this.msg2 = msg2; }
    public String getMsg3() { return msg3; }
    public void setMsg3(String msg3) { this.msg3 = msg3; }
    public String getMsg4() { return msg4; }
    public void setMsg4(String msg4) { this.msg4 = msg4; }
    public String getMsg5() { return msg5; }
    public void setMsg5(String msg5) { this.msg5 = msg5; }
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public String getMediaMime() { return mediaMime; }
    public void setMediaMime(String mediaMime) { this.mediaMime = mediaMime; }
    public String getMediaNom() { return mediaNom; }
    public void setMediaNom(String mediaNom) { this.mediaNom = mediaNom; }
    public Integer getAttenteMin() { return attenteMin; }
    public void setAttenteMin(Integer attenteMin) { this.attenteMin = attenteMin; }
    public Integer getAttenteMax() { return attenteMax; }
    public void setAttenteMax(Integer attenteMax) { this.attenteMax = attenteMax; }
    public Integer getPauseApres() { return pauseApres; }
    public void setPauseApres(Integer pauseApres) { this.pauseApres = pauseApres; }
    public Integer getPauseMin() { return pauseMin; }
    public void setPauseMin(Integer pauseMin) { this.pauseMin = pauseMin; }
    public Integer getPauseMax() { return pauseMax; }
    public void setPauseMax(Integer pauseMax) { this.pauseMax = pauseMax; }
    public String getDestinatairesTexte() { return destinatairesTexte; }
    public void setDestinatairesTexte(String destinatairesTexte) { this.destinatairesTexte = destinatairesTexte; }
}
