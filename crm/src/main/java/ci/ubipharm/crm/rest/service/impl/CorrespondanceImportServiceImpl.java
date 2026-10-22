package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.CorrespondanceImportService;
import ci.ubipharm.crm.rest.service.dto.ChampImportDTO;
import ci.ubipharm.crm.rest.service.dto.LigneTableurDTO;
import ci.ubipharm.crm.rest.service.dto.ModeleImportDTO;
import ci.ubipharm.crm.rest.service.dto.ParametresImportDTO;
import ci.ubipharm.crm.rest.service.dto.TableurDTO;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class CorrespondanceImportServiceImpl implements CorrespondanceImportService {

    private static final TypeReference<Map<String, String>> TYPE_CORRESPONDANCE = new TypeReference<>() {
    };

    private final EntityManager em;
    private final JsonMapper jsonMapper;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;

    @Override
    public Map<String, String> proposer(List<ChampImportDTO> champs, List<String> entetes) {
        Map<String, String> correspondance = new LinkedHashMap<>();
        Set<String> utilisees = new HashSet<>();
        for (ChampImportDTO champ : champs) {
            // Une colonne ne remplit qu'un champ : le premier qui la reconnait
            champ.getAlias().stream()
                    .filter(entetes::contains)
                    .filter(e -> !utilisees.contains(e))
                    .findFirst()
                    .ifPresent(e -> {
                        correspondance.put(champ.getCode(), e);
                        utilisees.add(e);
                    });
        }
        return correspondance;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> resoudre(String type, List<ChampImportDTO> champs, List<String> entetes, ParametresImportDTO parametres) {
        if (parametres != null && parametres.getCorrespondance() != null && !parametres.getCorrespondance().isEmpty()) {
            return nettoyer(parametres.getCorrespondance());
        }
        if (parametres != null && parametres.getModeleId() != null) {
            ModeleImportDTO modele = modele(parametres.getModeleId());
            if (!modele.getType().equals(type)) {
                throw new RessourceIntrouvableException("Modèle introuvable");
            }
            return modele.getCorrespondance();
        }
        return proposer(champs, entetes);
    }

    @Override
    public List<String> champsManquants(List<ChampImportDTO> champs, Map<String, String> correspondance, List<String> entetes) {
        return champs.stream()
                .filter(ChampImportDTO::isObligatoire)
                .filter(c -> correspondance.get(c.getCode()) == null || !entetes.contains(correspondance.get(c.getCode())))
                .map(ChampImportDTO::getLibelle)
                .toList();
    }

    @Override
    public List<LigneTableurDTO> appliquer(List<ChampImportDTO> champs, TableurDTO tableur, Map<String, String> correspondance) {
        Set<String> codes = champs.stream().map(ChampImportDTO::getCode).collect(Collectors.toSet());
        List<String> champsInconnus = correspondance.keySet().stream().filter(c -> !codes.contains(c)).toList();
        if (!champsInconnus.isEmpty()) {
            throw new ErreurMetierException("Champs inconnus dans la correspondance : " + String.join(", ", champsInconnus) + ".");
        }
        List<String> colonnesAbsentes = correspondance.values().stream().filter(c -> !tableur.getEntetes().contains(c)).distinct().toList();
        if (!colonnesAbsentes.isEmpty()) {
            throw new ErreurMetierException("Colonnes absentes du fichier : " + String.join(", ", colonnesAbsentes)
                    + ". Rechargez l'aperçu du fichier ou choisissez un autre modèle.");
        }
        List<String> manquants = champsManquants(champs, correspondance, tableur.getEntetes());
        if (!manquants.isEmpty()) {
            throw new ErreurMetierException("Champs obligatoires sans colonne : " + String.join(", ", manquants)
                    + ". Associez-les à une colonne du fichier, ou partez de l'exemplaire à télécharger.");
        }

        List<LigneTableurDTO> lignes = new ArrayList<>();
        for (LigneTableurDTO ligne : tableur.getLignes()) {
            Map<String, String> valeurs = new LinkedHashMap<>();
            correspondance.forEach((champ, colonne) -> {
                String valeur = ligne.getValeurs().get(colonne);
                if (valeur != null && !valeur.isBlank()) {
                    valeurs.put(champ, valeur);
                }
            });
            lignes.add(LigneTableurDTO.builder().numero(ligne.getNumero()).valeurs(valeurs).build());
        }
        return lignes;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModeleImportDTO> modeles(String type) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select m.id as id, m.type as type, m.nom as nom, m.correspondance as correspondance,
                       u.nom_complet as auteur, coalesce(m.updated_at, m.created_at) as modifieLe
                from crm_import_modele m join crm_utilisateur u on u.id = m.utilisateur_id
                where m.type = :type and m.statut = 'ACTIF'
                order by m.nom
                """, Tuple.class).setParameter("type", type).getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
    }

    @Override
    @Transactional
    public ModeleImportDTO enregistrerModele(String type, List<ChampImportDTO> champs, ModeleImportDTO modele) {
        Map<String, String> correspondance = nettoyer(modele.getCorrespondance());
        Set<String> codes = champs.stream().map(ChampImportDTO::getCode).collect(Collectors.toSet());
        List<String> champsInconnus = correspondance.keySet().stream().filter(c -> !codes.contains(c)).toList();
        if (!champsInconnus.isEmpty()) {
            throw new ErreurMetierException("Champs inconnus dans la correspondance : " + String.join(", ", champsInconnus) + ".");
        }
        String nom = modele.getNom().strip();
        String json = jsonMapper.writeValueAsString(correspondance);
        // Un modele archive du meme nom est reactive avec la nouvelle correspondance
        int misAJour = em.createNativeQuery("""
                update crm_import_modele set correspondance = :correspondance, statut = 'ACTIF',
                       utilisateur_id = :utilisateurId, updated_at = utc_timestamp()
                where type = :type and nom = :nom
                """)
                .setParameter("correspondance", json)
                .setParameter("utilisateurId", utilisateurCourantService.utilisateur().getId())
                .setParameter("type", type)
                .setParameter("nom", nom)
                .executeUpdate();
        if (misAJour == 0) {
            em.createNativeQuery("""
                    insert into crm_import_modele (type, nom, correspondance, utilisateur_id, created_at)
                    values (:type, :nom, :correspondance, :utilisateurId, utc_timestamp())
                    """)
                    .setParameter("type", type)
                    .setParameter("nom", nom)
                    .setParameter("correspondance", json)
                    .setParameter("utilisateurId", utilisateurCourantService.utilisateur().getId())
                    .executeUpdate();
        }
        Long id = ((Number) em.createNativeQuery("select id from crm_import_modele where type = :type and nom = :nom")
                .setParameter("type", type)
                .setParameter("nom", nom)
                .getSingleResult()).longValue();
        journalActionService.enregistrer("IMPORT_MODELE_ENREGISTREMENT", "crm_import_modele", id, type + " " + nom);
        return modele(id);
    }

    @Override
    @Transactional
    public void archiverModele(Long id) {
        int archives = em.createNativeQuery("update crm_import_modele set statut = 'ARCHIVE', updated_at = utc_timestamp() where id = :id and statut = 'ACTIF'")
                .setParameter("id", id)
                .executeUpdate();
        if (archives == 0) {
            throw new RessourceIntrouvableException("Modèle introuvable");
        }
        journalActionService.enregistrer("IMPORT_MODELE_ARCHIVAGE", "crm_import_modele", id, null);
    }

    private ModeleImportDTO modele(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select m.id as id, m.type as type, m.nom as nom, m.correspondance as correspondance,
                       u.nom_complet as auteur, coalesce(m.updated_at, m.created_at) as modifieLe
                from crm_import_modele m join crm_utilisateur u on u.id = m.utilisateur_id
                where m.id = :id and m.statut = 'ACTIF'
                """, Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Modèle introuvable");
        }
        return mapTupleToDto(lignes.getFirst());
    }

    /** Ecarte les champs laisses sans colonne. */
    private Map<String, String> nettoyer(Map<String, String> correspondance) {
        Map<String, String> propre = new LinkedHashMap<>();
        correspondance.forEach((champ, colonne) -> {
            if (champ != null && colonne != null && !colonne.isBlank()) {
                propre.put(champ.strip(), colonne.strip());
            }
        });
        return propre;
    }

    ModeleImportDTO mapTupleToDto(Tuple tuple) {
        return ModeleImportDTO.builder()
                .id(tuple.get("id", Long.class))
                .type(tuple.get("type", String.class))
                .nom(tuple.get("nom", String.class))
                .correspondance(jsonMapper.readValue(tuple.get("correspondance", String.class), TYPE_CORRESPONDANCE))
                .auteur(tuple.get("auteur", String.class))
                .modifieLe(tuple.get("modifieLe", LocalDateTime.class))
                .build();
    }
}
