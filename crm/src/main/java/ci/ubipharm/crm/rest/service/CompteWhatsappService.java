package ci.ubipharm.crm.rest.service;

import java.util.Optional;

/** Correspondance entre un numero ecrit par le CRM et le compte WhatsApp qui l'a recu. */
public interface CompteWhatsappService {

    /** Retient le compte indique par WhatsApp a l'envoi, quand il differe du numero ecrit. */
    void retenir(String expediteur, String numero, String compteWhatsapp);

    /** Numero du CRM dont ce compte WhatsApp a recu un message, s'il est connu. */
    Optional<String> numeroDuCompte(String compteWhatsapp);
}
