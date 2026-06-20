package com.ubisenderpro.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Rapport d'import (sections 10.6 et 25.6 de la spec).
 */
public class ImportReport {
    private Long importId;
    private String typeImport;
    private int lignesLues;
    private int comptesCrees;
    private int comptesMisAJour;
    private int contactsCrees;
    private int contactsMisAJour;
    private int contactsWhatsapp;
    private int contactsSansTelephone;
    private int lignesIgnorees;
    private int lignesRejetees;
    private final List<String> erreurs = new ArrayList<>();

    public void ajouterErreur(int ligne, String message) {
        erreurs.add("Ligne " + ligne + " : " + message);
    }

    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }
    public String getTypeImport() { return typeImport; }
    public void setTypeImport(String typeImport) { this.typeImport = typeImport; }
    public int getLignesLues() { return lignesLues; }
    public void setLignesLues(int lignesLues) { this.lignesLues = lignesLues; }
    public int getComptesCrees() { return comptesCrees; }
    public void setComptesCrees(int comptesCrees) { this.comptesCrees = comptesCrees; }
    public int getComptesMisAJour() { return comptesMisAJour; }
    public void setComptesMisAJour(int comptesMisAJour) { this.comptesMisAJour = comptesMisAJour; }
    public int getContactsCrees() { return contactsCrees; }
    public void setContactsCrees(int contactsCrees) { this.contactsCrees = contactsCrees; }
    public int getContactsMisAJour() { return contactsMisAJour; }
    public void setContactsMisAJour(int contactsMisAJour) { this.contactsMisAJour = contactsMisAJour; }
    public int getContactsWhatsapp() { return contactsWhatsapp; }
    public void setContactsWhatsapp(int contactsWhatsapp) { this.contactsWhatsapp = contactsWhatsapp; }
    public int getContactsSansTelephone() { return contactsSansTelephone; }
    public void setContactsSansTelephone(int contactsSansTelephone) { this.contactsSansTelephone = contactsSansTelephone; }
    public int getLignesIgnorees() { return lignesIgnorees; }
    public void setLignesIgnorees(int lignesIgnorees) { this.lignesIgnorees = lignesIgnorees; }
    public int getLignesRejetees() { return lignesRejetees; }
    public void setLignesRejetees(int lignesRejetees) { this.lignesRejetees = lignesRejetees; }
    public List<String> getErreurs() { return erreurs; }
}
