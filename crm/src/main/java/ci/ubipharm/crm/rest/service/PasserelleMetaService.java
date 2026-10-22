package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.ResultatEnvoiDTO;

/** Appels a l'API WhatsApp Cloud de Meta ; un numero y est designe par son identifiant Meta (phone_number_id). */
public interface PasserelleMetaService {

    /** Verifie le numero aupres de Meta avec le jeton du compte ; renvoie son numero affiche et son nom verifie. */
    String verifierNumero(String phoneNumberId);

    /** Texte libre : accepte par Meta seulement dans les 24 h qui suivent un message du client. Ne leve jamais d'exception. */
    ResultatEnvoiDTO envoyerTexte(String phoneNumberId, String destinataire, String texte);

    /** Image (avec legende eventuelle) ou document, dans la fenetre de 24 h. Ne leve jamais d'exception. */
    ResultatEnvoiDTO envoyerFichier(String phoneNumberId, String destinataire, FichierDTO fichier, String legende);

    /**
     * Modele approuve par Meta, seul envoi permis hors de la fenetre de 24 h : parametres {{1}}, {{2}}... du corps et,
     * si le modele en a un, fichier de l'en-tete (image ou document). Ne leve jamais d'exception.
     */
    ResultatEnvoiDTO envoyerModele(String phoneNumberId, String destinataire, String modele, String langue, List<String> parametres,
                                   FichierDTO entete);
}
