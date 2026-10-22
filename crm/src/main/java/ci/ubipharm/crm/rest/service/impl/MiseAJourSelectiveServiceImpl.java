package ci.ubipharm.crm.rest.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.MiseAJourSelectiveService;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.ReferentielService;
import ci.ubipharm.crm.rest.service.ResolutionReferentiels;
import ci.ubipharm.crm.rest.service.dto.LocalisationDTO;
import ci.ubipharm.crm.rest.service.dto.MiseAJourSelectiveDTO;
import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.rest.service.dto.ReferentielDTO;
import ci.ubipharm.crm.rest.service.dto.ResultatMiseAJourDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MiseAJourSelectiveServiceImpl implements MiseAJourSelectiveService {

    private static final Set<String> CHAMPS = Set.of("SEGMENT", "AGENCE", "SOUS_AGENCE", "LOCALISATION", "TOURNEE");

    private final EntityManager em;
    private final PerimetreService perimetreService;
    private final ReferentielService referentielService;
    private final JournalActionService journalActionService;

    private record Client(Long id, String code, Long agenceId, String statut, String segmentation, String sousAgence, Long regionId,
            Long villeId, Long communeId, Long tourneeId) {
    }

    @Override
    @Transactional
    public ResultatMiseAJourDTO appliquer(MiseAJourSelectiveDTO demande) {
        Set<String> champs = demande.getChamps();
        champs.stream().filter(c -> !CHAMPS.contains(c)).findFirst()
                .ifPresent(c -> { throw new ErreurMetierException("Champ non modifiable en masse : " + c + "."); });
        List<Client> clients = charger(demande.getClientIds());
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        List<String> motifs = new ArrayList<>();
        long horsPerimetre = clients.stream().filter(c -> !perimetre.isVisionGlobale() && !perimetre.getAgenceIds().contains(c.agenceId())).count();
        if (clients.size() < demande.getClientIds().stream().distinct().count() || horsPerimetre > 0) {
            motifs.add("Certains clients cochés sont introuvables ou hors de votre périmètre.");
        }
        long inactifs = clients.stream().filter(c -> !"ACTIF".equals(c.statut())).count();
        if (inactifs > 0) {
            motifs.add(inactifs + " client(s) coché(s) sont désactivés ou archivés : retirez-les de la sélection.");
        }

        String segment = null;
        if (champs.contains("SEGMENT") && demande.getSegmentation() != null && !demande.getSegmentation().isBlank()) {
            segment = demande.getSegmentation().trim().toUpperCase(Locale.ROOT);
            Number connu = (Number) em.createNativeQuery("select count(*) from crm_segment where code = :code and statut = 'ACTIF'")
                    .setParameter("code", segment).getSingleResult();
            if (connu.longValue() == 0) {
                motifs.add("Segment inconnu : " + segment + ".");
            }
        }
        if (champs.contains("AGENCE")) {
            Number connue = demande.getAgenceId() == null ? 0
                    : (Number) em.createNativeQuery("select count(*) from crm_agence where id = :id and statut = 'ACTIF'")
                            .setParameter("id", demande.getAgenceId()).getSingleResult();
            if (connue.longValue() == 0) {
                motifs.add("Choisissez une agence active.");
            } else if (!perimetre.isVisionGlobale() && !perimetre.getAgenceIds().contains(demande.getAgenceId())) {
                motifs.add("Cette agence est hors de votre périmètre.");
            }
        }
        ResolutionReferentiels resolution = referentielService.resolution();
        LocalisationDTO localisation = champs.contains("LOCALISATION")
                ? resolution.parIdentifiants(demande.getRegionId(), demande.getVilleId(), demande.getCommuneId(), null, null, Set.of(), motifs)
                : null;
        ReferentielDTO tournee = null;
        if (champs.contains("TOURNEE") && demande.getTourneeId() != null) {
            tournee = referentielService.lister("TOURNEE", false).stream().filter(t -> t.getId().equals(demande.getTourneeId())).findFirst()
                    .orElse(null);
            if (tournee == null) {
                motifs.add("Tournée inconnue ou archivée.");
            } else {
                Long tourneeAgence = tournee.getAgenceId();
                long autres = clients.stream()
                        .filter(c -> !Objects.equals(champs.contains("AGENCE") ? demande.getAgenceId() : c.agenceId(), tourneeAgence)).count();
                if (autres > 0) {
                    motifs.add("La tournée " + tournee.getLibelle() + " est desservie par l'agence " + tournee.getAgenceLibelle() + " : "
                            + autres + " client(s) coché(s) sont d'une autre agence.");
                }
            }
        }
        if (!motifs.isEmpty()) {
            throw new ErreurMetierException(String.join(" ", motifs));
        }

        Map<Long, String> agences = new java.util.HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> lignesAgences = em.createNativeQuery("select id, libelle from crm_agence").getResultList();
        lignesAgences.forEach(l -> agences.put(((Number) l[0]).longValue(), (String) l[1]));
        int modifies = 0;
        for (Client c : clients) {
            Map<String, Object> valeurs = new LinkedHashMap<>();
            List<String> ecarts = new ArrayList<>();
            if (champs.contains("SEGMENT")) {
                changer(valeurs, ecarts, "segmentation", "Segment", c.segmentation(), segment);
            }
            if (champs.contains("SOUS_AGENCE")) {
                String sousAgence = demande.getSousAgence() == null || demande.getSousAgence().isBlank() ? null
                        : demande.getSousAgence().trim().toUpperCase(Locale.ROOT);
                changer(valeurs, ecarts, "sous_agence", "Sous-agence", c.sousAgence(), sousAgence);
            }
            Long agence = c.agenceId();
            if (champs.contains("AGENCE")) {
                agence = demande.getAgenceId();
                changer(valeurs, ecarts, "agence_id", "Agence", c.agenceId(), agence, agences::get);
            }
            if (localisation != null) {
                changer(valeurs, ecarts, "region_id", "Région", c.regionId(), localisation.getRegionId(), resolution::libelle);
                changer(valeurs, ecarts, "ville_id", "Ville", c.villeId(), localisation.getVilleId(), resolution::libelle);
                changer(valeurs, ecarts, "commune_id", "Commune", c.communeId(), localisation.getCommuneId(), resolution::libelle);
            }
            if (champs.contains("TOURNEE")) {
                changer(valeurs, ecarts, "tournee_id", "Tournée", c.tourneeId(), tournee == null ? null : tournee.getId(), resolution::libelle);
            } else if (!Objects.equals(agence, c.agenceId()) && c.tourneeId() != null) {
                // La tournee appartient a l'ancienne agence : elle ne suit pas le client
                changer(valeurs, ecarts, "tournee_id", "Tournée", c.tourneeId(), null, resolution::libelle);
            }
            if (valeurs.isEmpty()) {
                continue;
            }
            StringBuilder sql = new StringBuilder("update crm_client set updated_at = utc_timestamp()");
            valeurs.keySet().forEach(col -> sql.append(", ").append(col).append(" = :").append(col));
            Query requete = em.createNativeQuery(sql + " where id = :id").setParameter("id", c.id());
            valeurs.forEach(requete::setParameter);
            requete.executeUpdate();
            journalActionService.enregistrer("CLIENT_MODIFICATION", "crm_client", c.id(), c.code() + " (mise à jour sélective) : " + String.join(" ; ", ecarts));
            modifies++;
        }
        return ResultatMiseAJourDTO.builder().nbModifies(modifies).nbInchanges(clients.size() - modifies).build();
    }

    private static void changer(Map<String, Object> valeurs, List<String> ecarts, String colonne, String libelle, Object avant, Object apres) {
        changer(valeurs, ecarts, colonne, libelle, avant, apres, v -> v == null ? null : String.valueOf(v));
    }

    /** Le journal montre les libelles, pas les identifiants. */
    private static <T> void changer(Map<String, Object> valeurs, List<String> ecarts, String colonne, String libelle, T avant, T apres,
            java.util.function.Function<T, String> lisible) {
        if (!Objects.equals(avant, apres)) {
            valeurs.put(colonne, apres);
            ecarts.add(libelle + " : " + (avant == null ? "vide" : lisible.apply(avant)) + " -> " + (apres == null ? "vide" : lisible.apply(apres)));
        }
    }

    private List<Client> charger(List<Long> ids) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select id as id, code as code, agence_id as agenceId, statut as statut, segmentation as segmentation, sous_agence as sousAgence,
                       region_id as regionId, ville_id as villeId, commune_id as communeId, tournee_id as tourneeId
                from crm_client where id in (:ids)
                """, Tuple.class).setParameter("ids", ids).getResultList();
        return lignes.stream().map(t -> new Client(id(t, "id"), t.get("code", String.class), id(t, "agenceId"), t.get("statut", String.class),
                t.get("segmentation", String.class), t.get("sousAgence", String.class), id(t, "regionId"), id(t, "villeId"), id(t, "communeId"),
                id(t, "tourneeId"))).toList();
    }

    private static Long id(Tuple t, String alias) {
        return t.get(alias) == null ? null : ((Number) t.get(alias)).longValue();
    }
}
