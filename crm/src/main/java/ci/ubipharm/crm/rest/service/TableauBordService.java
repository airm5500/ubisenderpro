package ci.ubipharm.crm.rest.service;

import java.time.LocalDate;

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
}
