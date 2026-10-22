package ci.ubipharm.crm.rest.service.impl;

/** Fragments SQL partages par les services du module Recouvrement. */
final class RequetesRecouvrement {

    /** L'utilisateur u est un agent de recouvrement actif. */
    static final String AGENT_RECOUVREMENT = """
            u.statut = 'ACTIF' and exists (select 1 from crm_habilitation h
                join crm_role r on r.id = h.role_id
                left join crm_module m on m.id = h.module_id
                where h.utilisateur_id = u.id and h.actif = true and r.code = 'AGENT'
                  and (h.module_id is null or m.code = 'RECOUVREMENT'))
            """;

    /** Agences de l'utilisateur u a la date du jour. */
    static final String AGENCES_EN_COURS = """
            select ua.agence_id from crm_utilisateur_agence ua
            where ua.utilisateur_id = u.id and ua.date_debut <= utc_date()
              and (ua.date_fin is null or ua.date_fin >= utc_date())
            """;

    /** La delegation d est en vigueur aujourd'hui. */
    static final String DELEGATION_EN_COURS = "d.statut = 'ACTIVE' and utc_date() between d.date_debut and d.date_fin";

    private RequetesRecouvrement() {
    }
}
