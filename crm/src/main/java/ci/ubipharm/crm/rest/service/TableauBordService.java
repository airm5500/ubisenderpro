package ci.ubipharm.crm.rest.service;

import java.time.LocalDate;
import java.util.List;

import ci.ubipharm.crm.rest.service.dto.ActiviteCalendrierDTO;
import ci.ubipharm.crm.rest.service.dto.EvolutionDTO;
import ci.ubipharm.crm.rest.service.dto.TableauBordDTO;
import ci.ubipharm.crm.rest.service.dto.TableauBordMarketingDTO;

public interface TableauBordService {

    /**
     * Recouvrement : stock du jour et activite de la periode (30 derniers jours par defaut), limites au perimetre de
     * l'utilisateur ; agence et agent restreignent encore.
     */
    TableauBordDTO recouvrement(LocalDate du, LocalDate au, Long agenceId, Long agentId);

    TableauBordMarketingDTO marketing(LocalDate du, LocalDate au);

    /**
     * Courbes du recouvrement sur la periode (un point par jour, par semaine au-dela de 60 jours) : relances envoyees,
     * distribuees et lues, montants encaisses et solde du echu, sur le meme perimetre que {@link #recouvrement}.
     */
    EvolutionDTO evolutionRecouvrement(LocalDate du, LocalDate au, Long agenceId, Long agentId);

    /** Courbes du marketing : messages de campagnes envoyes et lus, reponses recues et desabonnements STOP. */
    EvolutionDTO evolutionMarketing(LocalDate du, LocalDate au);

    /**
     * Calendrier d'activite du recouvrement (mois en cours par defaut, 62 jours au plus), jour par jour : relances envoyees et
     * en echec, campagnes lancees ou programmees, encaissements, promesses prises et a echeance, factures a echeance et reponses,
     * sur le meme perimetre que {@link #recouvrement}.
     */
    List<ActiviteCalendrierDTO> calendrierRecouvrement(LocalDate du, LocalDate au, Long agenceId, Long agentId);

    /** Calendrier d'activite du marketing : messages, campagnes, reponses, desabonnements, promotions et evenements. */
    List<ActiviteCalendrierDTO> calendrierMarketing(LocalDate du, LocalDate au);
}
