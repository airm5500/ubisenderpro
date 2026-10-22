package ci.ubipharm.crm.rest.service;

/**
 * Chiffrement des secrets gardes en base (jeton d'acces et secret de l'application Meta). La cle vit hors de la base,
 * dans le dossier donnees du serveur : une copie de la base seule ne livre pas les secrets.
 */
public interface ChiffrementService {

    /** Texte chiffre, prefixe pour le reconnaitre ; null ou vide reste tel quel. */
    String chiffrer(String clair);

    /** Texte en clair ; une valeur d'avant le chiffrement (sans prefixe) est rendue telle quelle. */
    String dechiffrer(String valeur);

    /** Vrai si la valeur est deja chiffree. */
    boolean estChiffre(String valeur);

    /** Chiffre au demarrage les secrets encore en clair en base. */
    void chiffrerSecretsEnClair();
}
