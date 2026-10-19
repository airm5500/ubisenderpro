package ci.ubipharm.crm.rest.service;

import java.time.LocalDate;

import ci.ubipharm.crm.rest.service.dto.TableauBordDTO;
import ci.ubipharm.crm.rest.service.dto.TableauBordMarketingDTO;

public interface TableauBordService {

    /**
     * Recouvrement : stock du jour et activite de la periode (30 derniers jours par defaut), limites au perimetre de
     * l'utilisateur ; agence et agent restreignent encore.
     */
    TableauBordDTO recouvrement(LocalDate du, LocalDate au, Long agenceId, Long agentId);

    TableauBordMarketingDTO marketing(LocalDate du, LocalDate au);
}
