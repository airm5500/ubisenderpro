package ci.ubipharm.crm.rest.service.impl;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.EvenementService;
import ci.ubipharm.crm.rest.service.MaJourneeMarketingService;
import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.EvenementDTO;
import ci.ubipharm.crm.rest.service.dto.MaJourneeMarketingDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MaJourneeMarketingServiceImpl implements MaJourneeMarketingService {

    static final String HORIZON = "marketing.journee_horizon_jours";

    private final EntityManager em;
    private final ParametreService parametreService;
    private final EvenementService evenementService;
    private final UtilisateurCourantService utilisateurCourantService;

    @Override
    @Transactional(readOnly = true)
    public MaJourneeMarketingDTO maJournee() {
        int horizon = Math.max(0, parametreService.entier(HORIZON, 7));
        LocalDate aujourdhui = LocalDate.now(ZoneOffset.UTC);
        LocalDate limite = aujourdhui.plusDays(horizon);
        MaJourneeMarketingDTO journee = MaJourneeMarketingDTO.builder().horizonJours(horizon).build();
        if (peut("DROIT_CAMPAGNES_MARKETING")) {
            journee.setNbReponsesDuJour(((Number) em.createNativeQuery("select count(*) " + REPONSES_DU_JOUR)
                    .setParameter("debut", aujourdhui.atStartOfDay()).getSingleResult()).longValue());
            @SuppressWarnings("unchecked")
            List<Tuple> lignes = em.createNativeQuery("""
                    select r.client_id as clientId, cl.raison_sociale as raisonSociale, r.numero_client as numeroClient, r.contenu as contenu,
                           r.recu_le as recuLe, c.id as campagneId, c.nom as campagneNom
                    """ + REPONSES_DU_JOUR + " order by r.recu_le desc, r.id desc", Tuple.class)
                    .setParameter("debut", aujourdhui.atStartOfDay()).setMaxResults(20).getResultList();
            journee.setReponsesDuJour(lignes.stream().map(t -> MaJourneeMarketingDTO.Reponse.builder()
                    .clientId(t.get("clientId", Long.class)).raisonSociale(t.get("raisonSociale", String.class))
                    .numeroClient(t.get("numeroClient", String.class)).contenu(t.get("contenu", String.class))
                    .recuLe(t.get("recuLe", LocalDateTime.class)).campagneId(t.get("campagneId", Long.class))
                    .campagneNom(t.get("campagneNom", String.class)).build()).toList());
        }
        if (peut("DROIT_PROMOTIONS")) {
            @SuppressWarnings("unchecked")
            List<Tuple> lignes = em.createNativeQuery("""
                    select p.id as id, p.nom as nom, p.date_debut as dateDebut, p.date_fin as dateFin, p.statut as statut
                    from crm_promotion p
                    where p.statut in ('PROGRAMMEE', 'ACTIVE')
                      and (p.date_debut between :du and :au or p.date_fin between :du and :au)
                    order by least(case when p.date_debut >= :du then p.date_debut else p.date_fin end, p.date_fin), p.nom
                    """, Tuple.class).setParameter("du", Date.valueOf(aujourdhui)).setParameter("au", Date.valueOf(limite)).getResultList();
            journee.setPromotions(lignes.stream().map(t -> {
                LocalDate debut = t.get("dateDebut", LocalDate.class);
                return MaJourneeMarketingDTO.Promotion.builder().id(t.get("id", Long.class)).nom(t.get("nom", String.class))
                        .dateDebut(debut).dateFin(t.get("dateFin", LocalDate.class)).statut(t.get("statut", String.class))
                        .moment(debut.isBefore(aujourdhui) ? "FINIT" : "COMMENCE").build();
            }).toList());
        }
        if (peut("DROIT_EVENEMENTS")) {
            journee.setEvenements(evenementService.lister(false).stream()
                    .filter(e -> e.getProchaineDate() != null && !e.getProchaineDate().isAfter(limite)
                            && (e.getProchaineFin() == null || !e.getProchaineFin().isBefore(aujourdhui)))
                    .sorted(java.util.Comparator.comparing(EvenementDTO::getProchaineDate)).toList());
        }
        return journee;
    }

    /** Reponses recues depuis minuit a des messages de campagnes Marketing. */
    private static final String REPONSES_DU_JOUR = """
            from crm_message_recu r
            join crm_campagne_message m on m.id = r.campagne_message_id
            join crm_campagne c on c.id = m.campagne_id and c.module = 'MARKETING'
            left join crm_client cl on cl.id = r.client_id
            where r.recu_le >= :debut
            """;

    private boolean peut(String autorite) {
        return utilisateurCourantService.utilisateur().getAuthorities().stream().anyMatch(a -> autorite.equals(a.getAuthority()));
    }
}
