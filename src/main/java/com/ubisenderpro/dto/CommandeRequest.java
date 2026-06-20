package com.ubisenderpro.dto;

import com.ubisenderpro.entity.Commande;
import com.ubisenderpro.entity.CommandeDetail;

import java.util.ArrayList;
import java.util.List;

public class CommandeRequest {
    private Commande commande;
    private List<CommandeDetail> lignes = new ArrayList<>();

    public Commande getCommande() { return commande; }
    public void setCommande(Commande commande) { this.commande = commande; }
    public List<CommandeDetail> getLignes() { return lignes; }
    public void setLignes(List<CommandeDetail> lignes) { this.lignes = lignes; }
}
