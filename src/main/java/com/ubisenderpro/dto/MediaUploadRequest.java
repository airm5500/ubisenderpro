package com.ubisenderpro.dto;

/**
 * Téléversement d'un fichier binaire vers l'API WhatsApp (endpoint /media).
 * Le fichier est transmis en base64 (même convention que l'assistant d'import).
 */
public class MediaUploadRequest {
    private Long accountId;
    private String fichierBase64;
    private String mimeType;
    private String nomFichier;

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getFichierBase64() { return fichierBase64; }
    public void setFichierBase64(String fichierBase64) { this.fichierBase64 = fichierBase64; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getNomFichier() { return nomFichier; }
    public void setNomFichier(String nomFichier) { this.nomFichier = nomFichier; }
}
