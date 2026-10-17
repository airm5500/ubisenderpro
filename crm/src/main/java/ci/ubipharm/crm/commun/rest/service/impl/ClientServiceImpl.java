package ci.ubipharm.crm.commun.rest.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.commun.rest.service.ClientService;
import ci.ubipharm.crm.commun.rest.service.dto.ClientCriteresDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ClientDTO;
import ci.ubipharm.crm.securite.rest.service.PerimetreService;
import ci.ubipharm.crm.securite.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClientServiceImpl implements ClientService {

    private static final String SELECT = """
            select c.id as id, c.code as code, c.raison_sociale as raisonSociale,
                   c.segmentation as segmentation, sg.libelle as segmentationLibelle,
                   c.nom_pharmacien as nomPharmacien, c.ville as ville, c.statut as statut,
                   a.id as agenceId, a.libelle as agenceLibelle, c.sous_agence as sousAgence, c.adresse as adresse,
                   c.email as email, c.commentaire as commentaire,
                   (select ct.numero_whatsapp from crm_contact ct
                     where ct.client_id = c.id and ct.rang = 1 and ct.statut = 'ACTIF' order by ct.id desc limit 1) as numeroPrincipal,
                   (select ct.numero_whatsapp from crm_contact ct
                     where ct.client_id = c.id and ct.rang = 2 and ct.statut = 'ACTIF'
                     order by field(ct.fonction, 'TITULAIRE', 'SECONDAIRE', 'ASSISTANT', 'CHARGE_COMMANDE', 'COMPTABLE'), ct.id
                     limit 1) as numeroSecondaire
            from crm_client c
            join crm_agence a on a.id = c.agence_id
            left join crm_segment sg on sg.code = c.segmentation
            """;

    private final EntityManager em;
    private final PerimetreService perimetreService;

    @Override
    @Transactional(readOnly = true)
    public PageDTO<ClientDTO> rechercher(ClientCriteresDTO criteres) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (!perimetre.isVisionGlobale() && perimetre.getAgenceIds().isEmpty()) {
            return PageDTO.<ClientDTO>builder().elements(List.of()).total(0).page(criteres.getPage()).taille(criteres.getTaille()).build();
        }

        // Le cloisonnement par agence est applique dans la requete, jamais en memoire
        StringBuilder where = new StringBuilder(" where c.statut = :statut");
        Map<String, Object> parametres = new LinkedHashMap<>();
        parametres.put("statut", criteres.getStatut());
        if (!perimetre.isVisionGlobale()) {
            where.append(" and c.agence_id in (:agencesVisibles)");
            parametres.put("agencesVisibles", perimetre.getAgenceIds());
        }
        if (criteres.getAgenceId() != null) {
            where.append(" and c.agence_id = :agenceId");
            parametres.put("agenceId", criteres.getAgenceId());
        }
        if (criteres.getSegmentation() != null && !criteres.getSegmentation().isBlank()) {
            where.append(" and c.segmentation = :segmentation");
            parametres.put("segmentation", criteres.getSegmentation().trim());
        }
        if (criteres.getQ() != null && !criteres.getQ().isBlank()) {
            where.append(" and (c.raison_sociale like :q or c.nom_pharmacien like :q or c.code like :q or exists (select 1 from crm_contact ct"
                    + " where ct.client_id = c.id and ct.numero_whatsapp like :q))");
            parametres.put("q", "%" + echapperLike(criteres.getQ().trim()) + "%");
        }

        Query comptage = em.createNativeQuery("select count(*) from crm_client c" + where);
        parametres.forEach(comptage::setParameter);
        long total = ((Number) comptage.getSingleResult()).longValue();

        Query requete = em.createNativeQuery(SELECT + where + " order by c.raison_sociale, c.id", Tuple.class);
        parametres.forEach(requete::setParameter);
        requete.setFirstResult(criteres.getPage() * criteres.getTaille());
        requete.setMaxResults(criteres.getTaille());
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();

        return PageDTO.<ClientDTO>builder()
                .elements(lignes.stream().map(this::mapTupleToDto).toList())
                .total(total)
                .page(criteres.getPage())
                .taille(criteres.getTaille())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ClientDTO consulter(Long id) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        String sql = SELECT + " where c.id = :id" + (perimetre.isVisionGlobale() ? "" : " and c.agence_id in (:agencesVisibles)");
        Query requete = em.createNativeQuery(sql, Tuple.class).setParameter("id", id);
        if (!perimetre.isVisionGlobale()) {
            if (perimetre.getAgenceIds().isEmpty()) {
                throw new RessourceIntrouvableException("Client introuvable");
            }
            requete.setParameter("agencesVisibles", perimetre.getAgenceIds());
        }
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        if (lignes.isEmpty()) {
            // Un client hors perimetre est traite comme inexistant
            throw new RessourceIntrouvableException("Client introuvable");
        }
        return mapTupleToDto(lignes.getFirst());
    }

    ClientDTO mapTupleToDto(Tuple tuple) {
        return ClientDTO.builder()
                .id(tuple.get("id", Long.class))
                .code(tuple.get("code", String.class))
                .raisonSociale(tuple.get("raisonSociale", String.class))
                .segmentation(tuple.get("segmentation", String.class))
                .segmentationLibelle(tuple.get("segmentationLibelle", String.class))
                .nomPharmacien(tuple.get("nomPharmacien", String.class))
                .ville(tuple.get("ville", String.class))
                .statut(tuple.get("statut", String.class))
                .agenceId(tuple.get("agenceId", Long.class))
                .agenceLibelle(tuple.get("agenceLibelle", String.class))
                .sousAgence(tuple.get("sousAgence", String.class))
                .adresse(tuple.get("adresse", String.class))
                .email(tuple.get("email", String.class))
                .commentaire(tuple.get("commentaire", String.class))
                .numeroPrincipal(tuple.get("numeroPrincipal", String.class))
                .numeroSecondaire(tuple.get("numeroSecondaire", String.class))
                .build();
    }

    private String echapperLike(String texte) {
        return texte.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
