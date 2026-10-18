package ci.ubipharm.crm.sms.rest.service;

import java.util.Map;

import ci.ubipharm.crm.sms.rest.service.dto.EtatSmsDTO;

/** Envoi des relances par SMS, au fil de l'eau, par le fournisseur par defaut. */
public interface EnvoiSmsService {

    /** Envoie le plus ancien SMS en attente si le debit le permet ; renvoie 1 s'il est parti. */
    int envoyerSuivant();

    /** Au demarrage : les SMS restes en cours d'envoi passent en echec a verifier, jamais renvoyes en double. */
    int reprendreEnvoisInterrompus();

    /** Interroge le fournisseur sur les SMS envoyes dont l'accuse n'est pas arrive ; renvoie le nombre de statuts mis a jour. */
    int verifierStatuts();

    /** Accuse de reception pousse par le fournisseur (corps JSON ou parametres de l'adresse). */
    void accuse(String code, String jeton, String corps, Map<String, String> parametres);

    EtatSmsDTO etat();
}
