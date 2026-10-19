package ci.ubipharm.crm.rest.service;

import java.util.List;
import java.util.Map;

import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.EtatSessionDTO;
import ci.ubipharm.crm.rest.service.dto.ResultatEnvoiDTO;

/** Appels au service compagnon wa-web (WhatsApp Web, Baileys) ; la session porte le numero de l'agent. */
public interface PasserelleWhatsappService {

    /** Demarre ou relance la session ; renvoie le QR a scanner si le telephone n'est pas encore lie. */
    EtatSessionDTO demarrer(String numero);

    EtatSessionDTO etat(String numero);

    void deconnecter(String numero);

    /** Ne leve jamais d'exception : l'issue est decrite dans le resultat. */
    ResultatEnvoiDTO envoyerTexte(String numero, String destinataire, String texte);

    /** Image (avec legende eventuelle) ou document ; ne leve jamais d'exception. */
    ResultatEnvoiDTO envoyerFichier(String numero, String destinataire, FichierDTO fichier, String legende);

    /** Presence sur WhatsApp de chaque numero (format international), interrogee depuis la session du numero donne. */
    Map<String, Boolean> verifierNumeros(String numero, List<String> numeros);

    /** Vrai si le service wa-web repond (route /health) ; ne leve jamais d'exception. */
    boolean joignable();
}
