package ci.ubipharm.crm.rest.service;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import ci.ubipharm.crm.rest.service.dto.LocalisationDTO;
import ci.ubipharm.crm.rest.service.dto.ReferentielDTO;

/**
 * Controle la localisation d'un client contre les referentiels : la commune donne sa ville, la ville sa region,
 * la tournee doit desservir l'agence du client. Charge une fois, sert a toutes les lignes d'un import.
 */
public final class ResolutionReferentiels {

    private final Map<Long, ReferentielDTO> parId = new HashMap<>();
    private final Map<String, List<ReferentielDTO>> parType;

    public ResolutionReferentiels(List<ReferentielDTO> entrees) {
        entrees.forEach(e -> parId.put(e.getId(), e));
        parType = entrees.stream().collect(Collectors.groupingBy(ReferentielDTO::getType));
    }

    /** Fiche saisie a la main : identifiants choisis dans les listes ; une valeur archivee n'est gardee que si le client la portait deja. */
    public LocalisationDTO parIdentifiants(Long regionId, Long villeId, Long communeId, Long tourneeId, Long agenceId, Set<Long> dejaPortes,
            List<String> motifs) {
        ReferentielDTO region = choisi("REGION", regionId, dejaPortes, motifs);
        ReferentielDTO ville = choisi("VILLE", villeId, dejaPortes, motifs);
        ReferentielDTO commune = choisi("COMMUNE", communeId, dejaPortes, motifs);
        ReferentielDTO tournee = choisi("TOURNEE", tourneeId, dejaPortes, motifs);
        return completer(region, ville, commune, tournee, agenceId, motifs);
    }

    /** Ligne d'import : libelles (ou codes) compares sans accent ni casse aux entrees actives. */
    public LocalisationDTO parLibelles(String region, String ville, String commune, String tournee, Long agenceId, List<String> motifs) {
        ReferentielDTO r = trouver("REGION", region, null, null, "Région", motifs);
        ReferentielDTO v = trouver("VILLE", ville, null, null, "Ville", motifs);
        ReferentielDTO c = trouver("COMMUNE", commune, v == null ? null : v.getId(), null, "Commune", motifs);
        ReferentielDTO t = trouver("TOURNEE", tournee, null, agenceId, "Tournée", motifs);
        return completer(r, v, c, t, agenceId, motifs);
    }

    private ReferentielDTO choisi(String type, Long id, Set<Long> dejaPortes, List<String> motifs) {
        if (id == null) {
            return null;
        }
        ReferentielDTO e = parId.get(id);
        if (e == null || !type.equals(e.getType())) {
            motifs.add(nom(type) + " inconnue.");
            return null;
        }
        if (!"ACTIF".equals(e.getStatut()) && !dejaPortes.contains(id)) {
            motifs.add(nom(type) + " archivée : " + e.getLibelle() + ".");
            return null;
        }
        return e;
    }

    private ReferentielDTO trouver(String type, String saisie, Long villeId, Long agenceId, String libelle, List<String> motifs) {
        if (saisie == null || saisie.isBlank()) {
            return null;
        }
        String cle = cle(saisie);
        List<ReferentielDTO> candidats = parType.getOrDefault(type, List.of()).stream()
                .filter(e -> "ACTIF".equals(e.getStatut()))
                .filter(e -> cle.equals(cle(e.getLibelle())) || e.getCode() != null && cle.equals(cle(e.getCode())))
                .toList();
        if (villeId != null && candidats.size() > 1) {
            candidats = candidats.stream().filter(e -> villeId.equals(e.getParentId())).toList();
        }
        if (agenceId != null && candidats.size() > 1) {
            candidats = candidats.stream().filter(e -> agenceId.equals(e.getAgenceId())).toList();
        }
        if (candidats.isEmpty()) {
            motifs.add(libelle + " inconnue du référentiel : " + saisie.trim() + " (à ajouter dans Paramètres, Référentiels).");
            return null;
        }
        if (candidats.size() > 1) {
            motifs.add(libelle + " ambiguë : " + saisie.trim() + " existe plusieurs fois ; précisez " + ("COMMUNE".equals(type) ? "la ville." : "l'agence."));
            return null;
        }
        return candidats.getFirst();
    }

    private LocalisationDTO completer(ReferentielDTO region, ReferentielDTO ville, ReferentielDTO commune, ReferentielDTO tournee, Long agenceId,
            List<String> motifs) {
        if (commune != null) {
            ReferentielDTO villeCommune = parId.get(commune.getParentId());
            if (ville != null && !ville.getId().equals(villeCommune.getId())) {
                motifs.add("La commune " + commune.getLibelle() + " n'est pas dans la ville " + ville.getLibelle() + ".");
            }
            ville = villeCommune;
        }
        if (ville != null && ville.getParentId() != null) {
            ReferentielDTO regionVille = parId.get(ville.getParentId());
            if (region != null && !region.getId().equals(regionVille.getId())) {
                motifs.add("La ville " + ville.getLibelle() + " n'est pas dans la région " + region.getLibelle() + ".");
            }
            region = regionVille;
        }
        if (tournee != null && !Objects.equals(tournee.getAgenceId(), agenceId)) {
            motifs.add("La tournée " + tournee.getLibelle() + " est desservie par l'agence " + tournee.getAgenceLibelle() + ".");
        }
        return LocalisationDTO.builder()
                .regionId(region == null ? null : region.getId()).region(region == null ? null : region.getLibelle())
                .villeId(ville == null ? null : ville.getId()).ville(ville == null ? null : ville.getLibelle())
                .communeId(commune == null ? null : commune.getId()).commune(commune == null ? null : commune.getLibelle())
                .tourneeId(tournee == null ? null : tournee.getId()).tournee(tournee == null ? null : tournee.getLibelle())
                .build();
    }

    private static String nom(String type) {
        return switch (type) {
            case "REGION" -> "Région";
            case "VILLE" -> "Ville";
            case "COMMUNE" -> "Commune";
            default -> "Tournée";
        };
    }

    private static String cle(String texte) {
        return Normalizer.normalize(texte.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[\\s'’_-]+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
