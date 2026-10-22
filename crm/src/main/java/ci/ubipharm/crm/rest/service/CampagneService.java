package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.AnalyseCampagneDTO;
import ci.ubipharm.crm.rest.service.dto.CampagneCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.CampagneDTO;
import ci.ubipharm.crm.rest.service.dto.CampagneDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.MessageCampagneCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.MessageCampagneDTO;
import ci.ubipharm.crm.rest.service.dto.MotifDTO;
import ci.ubipharm.crm.rest.service.dto.RecapitulatifCampagneDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;

/**
 * Cycle d'une campagne : creer, soumettre, valider (ce qui fige les messages), puis execution a la date prevue.
 * La validation ne vaut pas envoi immediat (cahier des charges 4.4).
 */
public interface CampagneService {

    PageDTO<CampagneDTO> rechercher(CampagneCriteresDTO criteres);

    CampagneDTO consulter(Long id);

    CampagneDTO creer(CampagneDemandeDTO demande);

    /** Brouillon ou campagne a valider ; elle redevient brouillon. */
    CampagneDTO modifier(Long id, CampagneDemandeDTO demande);

    /** Clients, factures, messages, agents et exclusions ; calcule en direct tant que la campagne n'est pas validee. */
    RecapitulatifCampagneDTO recapitulatif(Long id);

    PageDTO<MessageCampagneDTO> messages(Long id, MessageCampagneCriteresDTO criteres);

    CampagneDTO soumettre(Long id);

    /** Fige un message par facture ; demarre aussitot si la date d'execution est vide ou passee. */
    CampagneDTO valider(Long id);

    CampagneDTO rejeter(Long id, MotifDTO motif);

    /** Demarre maintenant une campagne validee et programmee plus tard. */
    CampagneDTO lancer(Long id);

    CampagneDTO annuler(Long id, MotifDTO motif);

    /** Demarre les campagnes validees dont la date d'execution est atteinte ; renvoie leur nombre. */
    int demarrerCampagnesEchues();

    /** Chiffres, taux et courbe jour par jour d'une campagne visible de l'utilisateur. */
    AnalyseCampagneDTO analyse(Long id);
}
