package com.ubisenderpro.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * Paramètres d'un import de clients/contacts.
 * mapping : champ logique -> nom de colonne dans le fichier.
 * Champs logiques reconnus : numero_client, nom_compte, contact_principal,
 * telephone_principal, telephone_2, numero_whatsapp, fonction, agence, region,
 * email_principal, segmentation, adresse, ville, commune, pays, statut, notes,
 * consentement_whatsapp.
 */
public class ImportClientRequest {
    private String nomFichier;
    private String separateur = ";";
    private Map<String, String> mapping = new HashMap<>();
    private Long mappingId;
    private boolean creerSegmentation = true;
    private boolean simulation = false;
    private String fichierBase64;
    /** Comportement en cas de doublon : AJOUT_MAJ, IGNORER, COMPLETER_VIDES. */
    private String mode = "AJOUT_MAJ";

    public String getNomFichier() { return nomFichier; }
    public void setNomFichier(String nomFichier) { this.nomFichier = nomFichier; }
    public String getSeparateur() { return separateur; }
    public void setSeparateur(String separateur) { this.separateur = separateur; }
    public Map<String, String> getMapping() { return mapping; }
    public void setMapping(Map<String, String> mapping) { this.mapping = mapping; }
    public Long getMappingId() { return mappingId; }
    public void setMappingId(Long mappingId) { this.mappingId = mappingId; }
    public boolean isCreerSegmentation() { return creerSegmentation; }
    public void setCreerSegmentation(boolean creerSegmentation) { this.creerSegmentation = creerSegmentation; }
    public boolean isSimulation() { return simulation; }
    public void setSimulation(boolean simulation) { this.simulation = simulation; }
    public String getFichierBase64() { return fichierBase64; }
    public void setFichierBase64(String fichierBase64) { this.fichierBase64 = fichierBase64; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
