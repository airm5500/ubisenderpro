package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.AnalyseCampagneDTO;
import ci.ubipharm.crm.rest.service.dto.CampagneCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.CampagneMarketingDTO;
import ci.ubipharm.crm.rest.service.dto.CampagneMarketingDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.MessageCampagneCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.MessageCampagneDTO;
import ci.ubipharm.crm.rest.service.dto.MotifDTO;
import ci.ubipharm.crm.rest.service.dto.RecapitulatifMarketingDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;

/** Campagnes promotionnelles du Marketing : meme cycle que les relances (brouillon, validation, envoi). */
public interface CampagneMarketingService {

    PageDTO<CampagneMarketingDTO> rechercher(CampagneCriteresDTO criteres);

    CampagneMarketingDTO consulter(Long id);

    CampagneMarketingDTO creer(CampagneMarketingDemandeDTO demande);

    /** Brouillon ou campagne a valider ; elle redevient brouillon. */
    CampagneMarketingDTO modifier(Long id, CampagneMarketingDemandeDTO demande);

    /** Calcule en direct tant que la campagne n'est pas validee. */
    RecapitulatifMarketingDTO recapitulatif(Long id);

    PageDTO<MessageCampagneDTO> messages(Long id, MessageCampagneCriteresDTO criteres);

    CampagneMarketingDTO soumettre(Long id);

    /** Fige un message par numero (ou adresse) ; demarre aussitot si la date d'execution est vide ou passee. */
    CampagneMarketingDTO valider(Long id);

    CampagneMarketingDTO rejeter(Long id, MotifDTO motif);

    CampagneMarketingDTO lancer(Long id);

    CampagneMarketingDTO annuler(Long id, MotifDTO motif);

    int demarrerCampagnesEchues();

    /** Chiffres, taux et courbe jour par jour d'une campagne visible de l'utilisateur. */
    AnalyseCampagneDTO analyse(Long id);
}
