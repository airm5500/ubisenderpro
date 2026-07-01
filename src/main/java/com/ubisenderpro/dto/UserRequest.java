package com.ubisenderpro.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Données de création/modification d'un utilisateur applicatif.
 * roles = liste de codes de rôle (ADMIN, MARKETING, SUPERVISEUR, AGENT, CATALOGUE, LECTURE).
 */
public class UserRequest {
    private String login;
    private String nomComplet;
    private String avatar;
    private String photo;
    private String email;
    private String agence;
    private String motDePasse;
    private Boolean actif;
    private List<String> roles = new ArrayList<>();

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAgence() { return agence; }
    public void setAgence(String agence) { this.agence = agence; }
    public String getMotDePasse() { return motDePasse; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }
    public Boolean getActif() { return actif; }
    public void setActif(Boolean actif) { this.actif = actif; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
}
