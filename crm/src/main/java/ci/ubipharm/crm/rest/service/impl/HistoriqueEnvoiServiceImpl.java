package ci.ubipharm.crm.rest.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.ArchivageService;
import ci.ubipharm.crm.rest.service.HistoriqueEnvoiService;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.PortefeuilleService;
import ci.ubipharm.crm.rest.service.dto.EnvoiHistoriqueCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiHistoriqueDTO;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HistoriqueEnvoiServiceImpl implements HistoriqueEnvoiService {

    private static final int EXPORT_MAX = 50_000;
    private static final DateTimeFormatter DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Les deux sources d'envois, ramenees aux memes colonnes. */
    private static final String ENVOIS = """
            (select concat('C', m.id) as cle, case cp.module when 'MARKETING' then 'PROMOTION' else 'RELANCE' end as origine, cp.nom as source,
                    m.canal as canal, m.numero_expediteur as expediteur, coalesce(m.agent_id, cp.cree_par) as utilisateur_id,
                    coalesce(m.numero_destinataire, m.email_destinataire) as destinataire, m.client_id as client_id, m.contenu as contenu,
                    m.statut as statut, m.motif as motif, coalesce(m.envoye_le, m.created_at) as le, m.distribue_le as distribue_le, m.lu_le as lu_le
             from crm_campagne_message m join crm_campagne cp on cp.id = m.campagne_id
             union all
             select concat('S', s.id), s.origine, coalesce(e.nom, 'Discussion'), 'WHATSAPP', s.numero_expediteur, s.cree_par, s.numero_destinataire,
                    s.client_id, s.contenu, s.statut, s.motif, coalesce(s.envoye_le, s.created_at), s.distribue_le, s.lu_le
             from crm_message_sortant s left join crm_envoi_masse e on e.id = s.envoi_masse_id) h
            left join crm_utilisateur u on u.id = h.utilisateur_id
            left join crm_client c on c.id = h.client_id
            """;

    private static final String SELECT = """
            select h.cle as cle, h.origine as origine, h.source as source, h.canal as canal, h.expediteur as expediteur, u.nom_complet as utilisateurNom,
                   h.destinataire as destinataire, h.client_id as clientId, c.raison_sociale as clientNom, h.contenu as contenu, h.statut as statut,
                   h.motif as motif, h.le as le, h.distribue_le as distribueLe, h.lu_le as luLe
            from """;

    private final EntityManager em;
    private final PerimetreService perimetreService;
    private final PortefeuilleService portefeuilleService;
    private final ArchivageService archivageService;

    @Override
    @Transactional(readOnly = true)
    public PageDTO<EnvoiHistoriqueDTO> rechercher(EnvoiHistoriqueCriteresDTO criteres) {
        Map<String, Object> parametres = new LinkedHashMap<>();
        String where = where(criteres, parametres);
        Query comptage = em.createNativeQuery("select count(*) from " + ENVOIS + where);
        parametres.forEach(comptage::setParameter);
        long total = ((Number) comptage.getSingleResult()).longValue();
        Query requete = em.createNativeQuery(SELECT + ENVOIS + where + " order by h.le desc, h.cle desc", Tuple.class);
        parametres.forEach(requete::setParameter);
        requete.setFirstResult(criteres.getPage() * criteres.getTaille());
        requete.setMaxResults(criteres.getTaille());
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return PageDTO.<EnvoiHistoriqueDTO>builder().elements(lignes.stream().map(this::mapTupleToDto).toList())
                .total(total).page(criteres.getPage()).taille(criteres.getTaille()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public FichierDTO exporter(EnvoiHistoriqueCriteresDTO criteres) {
        Map<String, Object> parametres = new LinkedHashMap<>();
        Query requete = em.createNativeQuery(SELECT + ENVOIS + where(criteres, parametres) + " order by h.le desc, h.cle desc", Tuple.class);
        parametres.forEach(requete::setParameter);
        requete.setMaxResults(EXPORT_MAX);
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        StringBuilder csv = new StringBuilder("﻿Date;Origine;Campagne ou envoi;Canal;Expéditeur;Envoyé par;Destinataire;Client;Statut;Motif;Distribué le;Lu le;Message\r\n");
        for (Tuple t : lignes) {
            EnvoiHistoriqueDTO e = mapTupleToDto(t);
            csv.append(String.join(";", date(e.getLe()), e.getOrigine(), cellule(e.getSource()), e.getCanal(), cellule(e.getExpediteur()),
                    cellule(e.getUtilisateurNom()), cellule(e.getDestinataire()), cellule(e.getClientNom()), e.getStatut(), cellule(e.getMotif()),
                    date(e.getDistribueLe()), date(e.getLuLe()), cellule(e.getContenu()))).append("\r\n");
        }
        byte[] contenu = csv.toString().getBytes(StandardCharsets.UTF_8);
        String nom = "historique-envois-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".csv";
        archivageService.archiver("exports-historique", nom, contenu);
        return FichierDTO.builder().nom(nom).typeContenu("text/csv;charset=UTF-8").contenu(contenu).build();
    }

    private String where(EnvoiHistoriqueCriteresDTO criteres, Map<String, Object> parametres) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Long> agentsGeres = portefeuilleService.agentsGeres();
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (agentsGeres != null) {
            where.append(" and h.utilisateur_id in (:agentsGeres)");
            parametres.put("agentsGeres", agentsGeres);
        } else if (!perimetre.isVisionGlobale()) {
            // La direction d'agence voit les envois vers ses clients, et les siens
            where.append(" and (c.agence_id in (:agences) or h.utilisateur_id = :moi)");
            parametres.put("agences", perimetre.getAgenceIds().isEmpty() ? List.of(0L) : perimetre.getAgenceIds());
            parametres.put("moi", perimetre.getUtilisateurId());
        }
        if (criteres.getDu() != null) {
            where.append(" and h.le >= :du");
            parametres.put("du", criteres.getDu().atStartOfDay());
        }
        if (criteres.getAu() != null) {
            where.append(" and h.le < :au");
            parametres.put("au", criteres.getAu().plusDays(1).atStartOfDay());
        }
        ajouter(where, parametres, "h.origine", "origine", criteres.getOrigine());
        ajouter(where, parametres, "h.canal", "canal", criteres.getCanal());
        ajouter(where, parametres, "h.statut", "statut", criteres.getStatut());
        ajouter(where, parametres, "h.utilisateur_id", "utilisateurId", criteres.getUtilisateurId());
        ajouter(where, parametres, "h.client_id", "clientId", criteres.getClientId());
        if (criteres.getQ() != null && !criteres.getQ().isBlank()) {
            String q = criteres.getQ().trim();
            String chiffres = q.replaceAll("\\D", "");
            where.append(" and (h.destinataire like :q or c.raison_sociale like :q or c.code = :code");
            parametres.put("q", "%" + q + "%");
            parametres.put("code", q);
            if (chiffres.length() >= 6) {
                // 07 00 00 00 01 retrouve 2250700000001
                where.append(" or h.destinataire like :chiffres");
                parametres.put("chiffres", "%" + chiffres.substring(chiffres.length() - Math.min(10, chiffres.length())));
            }
            where.append(")");
        }
        return where.toString();
    }

    private static void ajouter(StringBuilder where, Map<String, Object> parametres, String colonne, String nom, Object valeur) {
        if (valeur != null && !(valeur instanceof String s && s.isBlank())) {
            where.append(" and ").append(colonne).append(" = :").append(nom);
            parametres.put(nom, valeur);
        }
    }

    private static String date(LocalDateTime le) {
        return le == null ? "" : le.format(DATE_HEURE);
    }

    private static String cellule(String valeur) {
        return valeur == null ? "" : "\"" + valeur.replace("\"", "\"\"").replace("\r", "").replace("\n", " ") + "\"";
    }

    EnvoiHistoriqueDTO mapTupleToDto(Tuple tuple) {
        return EnvoiHistoriqueDTO.builder()
                .cle(tuple.get("cle", String.class))
                .origine(tuple.get("origine", String.class))
                .source(tuple.get("source", String.class))
                .canal(tuple.get("canal", String.class))
                .expediteur(tuple.get("expediteur", String.class))
                .utilisateurNom(tuple.get("utilisateurNom", String.class))
                .destinataire(tuple.get("destinataire", String.class))
                .clientId(tuple.get("clientId", Long.class))
                .clientNom(tuple.get("clientNom", String.class))
                .contenu(tuple.get("contenu", String.class))
                .statut(tuple.get("statut", String.class))
                .motif(tuple.get("motif", String.class))
                .le(tuple.get("le", LocalDateTime.class))
                .distribueLe(tuple.get("distribueLe", LocalDateTime.class))
                .luLe(tuple.get("luLe", LocalDateTime.class))
                .build();
    }
}
