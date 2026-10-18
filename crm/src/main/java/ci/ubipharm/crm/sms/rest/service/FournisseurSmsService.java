package ci.ubipharm.crm.sms.rest.service;

import java.util.List;

import ci.ubipharm.crm.sms.rest.service.dto.EssaiSmsDTO;
import ci.ubipharm.crm.sms.rest.service.dto.FournisseurSmsDTO;
import ci.ubipharm.crm.sms.rest.service.dto.FournisseurSmsDemandeDTO;
import ci.ubipharm.crm.sms.rest.service.dto.ResultatAppelSmsDTO;

/** Parametrage des fournisseurs SMS, reserve a l'administrateur. Un fournisseur n'est jamais supprime : il est desactive. */
public interface FournisseurSmsService {

    List<FournisseurSmsDTO> lister();

    FournisseurSmsDTO consulter(Long id);

    FournisseurSmsDTO creer(FournisseurSmsDemandeDTO demande);

    FournisseurSmsDTO modifier(Long id, FournisseurSmsDemandeDTO demande);

    /** Le fournisseur devient celui des relances SMS ; il doit etre actif. */
    FournisseurSmsDTO definirParDefaut(Long id);

    /** Envoie un SMS d'essai et renvoie ce que le fournisseur a repondu. */
    ResultatAppelSmsDTO essayer(Long id, EssaiSmsDTO essai);

    ResultatAppelSmsDTO solde(Long id);
}
