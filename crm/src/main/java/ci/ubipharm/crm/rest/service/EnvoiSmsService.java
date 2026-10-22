package ci.ubipharm.crm.rest.service;

import java.util.Map;

import ci.ubipharm.crm.rest.service.dto.EtatSmsDTO;

/** Envoi des relances par SMS, au fil de l'eau, par le fournisseur par defaut. */
public interface EnvoiSmsService {

    /** Envoie le plus ancien SMS en attente si le debit le permet ; renvoie 1 s'il est parti. */
    int envoyerSuivant();

    /**
     * Envoie aussitot ce message en attente, hors plage horaire, debit et plafond (demande d'Airman du 20/10/2026) ;
     * false s'il n'est plus en attente ou que sa campagne n'est pas en cours.
     */
    boolean envoyerMaintenant(Long messageId);

    /** Au demarrage : les SMS restes en cours d'envoi passent en echec a verifier, jamais renvoyes en double. */
    int reprendreEnvoisInterrompus();

    /** Interroge le fournisseur sur les SMS envoyes dont l'accuse n'est pas arrive ; renvoie le nombre de statuts mis a jour. */
    int verifierStatuts();

    /** Accuse de reception pousse par le fournisseur (corps JSON ou parametres de l'adresse). */
    void accuse(String code, String jeton, String corps, Map<String, String> parametres);

    EtatSmsDTO etat();
}
