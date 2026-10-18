package ci.ubipharm.crm.whatsapp.rest.service;

import ci.ubipharm.crm.whatsapp.rest.service.dto.EtatSessionDTO;
import ci.ubipharm.crm.whatsapp.rest.service.dto.ResultatEnvoiDTO;

/** Appels au service compagnon wa-web (WhatsApp Web, Baileys) ; la session porte le numero de l'agent. */
public interface PasserelleWhatsappService {

    /** Demarre ou relance la session ; renvoie le QR a scanner si le telephone n'est pas encore lie. */
    EtatSessionDTO demarrer(String numero);

    EtatSessionDTO etat(String numero);

    void deconnecter(String numero);

    /** Ne leve jamais d'exception : l'issue est decrite dans le resultat. */
    ResultatEnvoiDTO envoyerTexte(String numero, String destinataire, String texte);
}
