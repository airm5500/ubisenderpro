package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.ListeDiffusionService;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.dto.ApercuListeDTO;
import ci.ubipharm.crm.rest.service.dto.ClientsListeDTO;
import ci.ubipharm.crm.rest.service.dto.ListeDiffusionDTO;
import ci.ubipharm.crm.rest.service.dto.ListeDiffusionDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.MembreListeDTO;
import ci.ubipharm.crm.rest.service.dto.MembresCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.rest.service.dto.ValeursCriteresDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ListeDiffusionServiceImpl implements ListeDiffusionService {

    private static final String SELECT_LISTE = """
            select l.id as id, l.nom as nom, l.description as description, l.par_criteres as parCriteres, l.agence_ids as agenceIds,
                   l.sous_agences as sousAgences, l.villes as villes, l.segments as segments, l.fonctions as fonctions, l.statut as statut,
                   u.nom_complet as creePar, l.created_at as creeLe, l.updated_at as modifieLe,
                   (select count(*) from crm_liste_diffusion_client x where x.liste_id = l.id and x.actif = true and x.sens = 'AJOUT') as nbAjouts,
                   (select count(*) from crm_liste_diffusion_client x where x.liste_id = l.id and x.actif = true and x.sens = 'EXCLUSION') as nbExclusions
            from crm_liste_diffusion l join crm_utilisateur u on u.id = l.cree_par
            """;

    private final EntityManager em;
    private final PerimetreService perimetreService;
    private final JournalActionService journalActionService;

    /** Definition d'une liste, enregistree ou en cours de saisie. */
    private record Definition(Long id, boolean parCriteres, List<Long> agenceIds, List<String> sousAgences, List<String> villes,
                              List<String> segments, List<String> fonctions) {
    }

    @Override
    @Transactional(readOnly = true)
    public List<ListeDiffusionDTO> lister(boolean avecArchivees) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT_LISTE + (avecArchivees ? "" : " where l.statut = 'ACTIF'") + " order by l.statut, l.nom",
                Tuple.class).getResultList();
        return lignes.stream().map(this::avecCompteurs).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ListeDiffusionDTO consulter(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT_LISTE + " where l.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Liste introuvable");
        }
        return avecCompteurs(lignes.getFirst());
    }

    @Override
    @Transactional
    public ListeDiffusionDTO creer(ListeDiffusionDemandeDTO demande) {
        Definition d = verifier(null, demande);
        em.createNativeQuery("""
                insert into crm_liste_diffusion (nom, description, par_criteres, agence_ids, sous_agences, villes, segments, fonctions, statut,
                                                 cree_par, created_at)
                values (:nom, :description, :parCriteres, :agenceIds, :sousAgences, :villes, :segments, :fonctions, 'ACTIF', :par, utc_timestamp())
                """)
                .setParameter("nom", demande.getNom().trim())
                .setParameter("description", vide(demande.getDescription()))
                .setParameter("parCriteres", d.parCriteres())
                .setParameter("agenceIds", csv(d.agenceIds()))
                .setParameter("sousAgences", csv(d.sousAgences()))
                .setParameter("villes", csv(d.villes()))
                .setParameter("segments", csv(d.segments()))
                .setParameter("fonctions", csv(d.fonctions()))
                .setParameter("par", perimetreService.perimetreCourant().getUtilisateurId())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        journalActionService.enregistrer("LISTE_CREATION", "crm_liste_diffusion", id, demande.getNom().trim() + " : " + resume(d));
        return consulter(id);
    }

    @Override
    @Transactional
    public ListeDiffusionDTO modifier(Long id, ListeDiffusionDemandeDTO demande) {
        ListeDiffusionDTO avant = consulter(id);
        if ("ARCHIVE".equals(avant.getStatut())) {
            throw new ErreurMetierException("Cette liste est archivée : réactivez-la pour la modifier.");
        }
        Definition d = verifier(id, demande);
        em.createNativeQuery("""
                update crm_liste_diffusion set nom = :nom, description = :description, par_criteres = :parCriteres, agence_ids = :agenceIds,
                       sous_agences = :sousAgences, villes = :villes, segments = :segments, fonctions = :fonctions,
                       modifie_par = :par, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("nom", demande.getNom().trim())
                .setParameter("description", vide(demande.getDescription()))
                .setParameter("parCriteres", d.parCriteres())
                .setParameter("agenceIds", csv(d.agenceIds()))
                .setParameter("sousAgences", csv(d.sousAgences()))
                .setParameter("villes", csv(d.villes()))
                .setParameter("segments", csv(d.segments()))
                .setParameter("fonctions", csv(d.fonctions()))
                .setParameter("par", perimetreService.perimetreCourant().getUtilisateurId())
                .setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("LISTE_MODIFICATION", "crm_liste_diffusion", id,
                avant.getNom() + (avant.getNom().equals(demande.getNom().trim()) ? "" : " -> " + demande.getNom().trim()) + " : " + resume(d));
        return consulter(id);
    }

    @Override
    @Transactional
    public ListeDiffusionDTO archiver(Long id) {
        return changerStatut(id, "ARCHIVE", "LISTE_ARCHIVAGE");
    }

    @Override
    @Transactional
    public ListeDiffusionDTO reactiver(Long id) {
        return changerStatut(id, "ACTIF", "LISTE_REACTIVATION");
    }

    @Override
    @Transactional(readOnly = true)
    public ApercuListeDTO membres(Long id, MembresCriteresDTO criteres) {
        ListeDiffusionDTO liste = consulter(id);
        return apercu(definition(liste), criteres);
    }

    @Override
    @Transactional(readOnly = true)
    public ApercuListeDTO simuler(ListeDiffusionDemandeDTO demande) {
        // Le nom n'a pas a etre unique ni saisi pour un simple comptage
        Definition d = definition(null, demande);
        ApercuListeDTO apercu = apercu(d, MembresCriteresDTO.builder().taille(1).build());
        apercu.setMembres(null);
        return apercu;
    }

    @Override
    @Transactional
    public ListeDiffusionDTO changerClients(Long id, ClientsListeDTO demande) {
        ListeDiffusionDTO liste = consulter(id);
        if ("ARCHIVE".equals(liste.getStatut())) {
            throw new ErreurMetierException("Cette liste est archivée : réactivez-la pour la modifier.");
        }
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        Query requete = em.createNativeQuery("select id from crm_client where id in (:ids) and statut = 'ACTIF'"
                + (perimetre.isVisionGlobale() ? "" : " and agence_id in (:agences)"))
                .setParameter("ids", demande.getClientIds());
        if (!perimetre.isVisionGlobale()) {
            requete.setParameter("agences", perimetre.getAgenceIds().isEmpty() ? List.of(-1L) : perimetre.getAgenceIds());
        }
        @SuppressWarnings("unchecked")
        List<Number> trouves = requete.getResultList();
        if (trouves.size() != demande.getClientIds().stream().distinct().count()) {
            throw new RessourceIntrouvableException("Client introuvable");
        }
        for (Number clientId : trouves) {
            if ("RETRAIT".equals(demande.getSens())) {
                em.createNativeQuery("update crm_liste_diffusion_client set actif = false, updated_at = utc_timestamp() where liste_id = :liste and client_id = :client")
                        .setParameter("liste", id).setParameter("client", clientId.longValue()).executeUpdate();
            } else {
                // Une seule ligne par client et par liste : ajouter un client exclu l'y remet, et inversement
                em.createNativeQuery("""
                        insert into crm_liste_diffusion_client (liste_id, client_id, sens, actif, cree_par, created_at)
                        values (:liste, :client, :sens, true, :par, utc_timestamp())
                        on duplicate key update sens = :sens, actif = true, updated_at = utc_timestamp()
                        """)
                        .setParameter("liste", id).setParameter("client", clientId.longValue()).setParameter("sens", demande.getSens())
                        .setParameter("par", perimetre.getUtilisateurId()).executeUpdate();
            }
        }
        String action = switch (demande.getSens()) {
            case "AJOUT" -> "ajoutés";
            case "EXCLUSION" -> "exclus";
            default -> "retirés des ajouts et exclusions";
        };
        journalActionService.enregistrer("LISTE_CLIENTS", "crm_liste_diffusion", id, liste.getNom() + " : " + trouves.size() + " client(s) " + action);
        return consulter(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ValeursCriteresDTO valeursCriteres() {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        String filtre = " where statut = 'ACTIF'" + (perimetre.isVisionGlobale() ? "" : " and agence_id in (:agences)");
        return ValeursCriteresDTO.builder()
                .sousAgences(distincts("select distinct sous_agence from crm_client" + filtre + " and sous_agence is not null order by 1", perimetre))
                .villes(distincts("select distinct ville from crm_client" + filtre + " and ville is not null and ville <> '' order by 1", perimetre))
                .build();
    }

    // --- Resolution d'une liste ---------------------------------------------------------------

    /**
     * Clients de la liste dans le perimetre du lecteur : ajoutes a la main, ou retenus par les criteres sans avoir ete exclus.
     * Chaque client porte ses numeros retenus (fonctions de la liste) et son motif s'il n'est pas joignable.
     */
    private ApercuListeDTO apercu(Definition d, MembresCriteresDTO criteres) {
        Map<String, Object> parametres = new LinkedHashMap<>();
        String membres = requeteMembres(d, parametres);
        String motif = "case when c.desabonne_marketing_le is not null then 'DESABONNE'"
                + " when not exists (select 1 from crm_contact n where n.client_id = c.id and n.statut = 'ACTIF' and n.fonction in (:fonctions))"
                + " and (c.email is null or c.email = '') then 'SANS_COORDONNEES' end";
        parametres.put("fonctions", d.fonctions());

        Query compte = em.createNativeQuery("select count(*) as total, sum(m.motif is null) as joignables, sum(m.motif = 'DESABONNE') as desabonnes,"
                + " sum(m.motif = 'SANS_COORDONNEES') as sansCoordonnees from (select " + motif + " as motif " + membres + ") m", Tuple.class);
        parametres.forEach(compte::setParameter);
        Tuple t = (Tuple) compte.getSingleResult();
        ApercuListeDTO apercu = ApercuListeDTO.builder()
                .nbClients(nombre(t.get("total")))
                .nbJoignables(nombre(t.get("joignables")))
                .nbDesabonnes(nombre(t.get("desabonnes")))
                .nbSansCoordonnees(nombre(t.get("sansCoordonnees")))
                .build();

        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> filtres;
        String source;
        if ("EXCLUS".equals(criteres.getFiltre())) {
            // Les exclus a la main ne sont pas dans la liste : ils se listent a part, avec leurs seuls parametres
            filtres = new LinkedHashMap<>();
            filtres.put("fonctions", d.fonctions());
            filtres.put("liste", d.id() == null ? -1L : d.id());
            source = "select c.id as clientId, 'EXCLUSION' as origine, " + motif + " as motif from crm_client c"
                    + " join crm_liste_diffusion_client x on x.client_id = c.id and x.liste_id = :liste and x.actif = true and x.sens = 'EXCLUSION'"
                    + perimetreSql(filtres, "c");
        } else {
            filtres = new LinkedHashMap<>(parametres);
            source = "select c.id as clientId, " + origine(d) + " as origine, " + motif + " as motif " + membres;
            if ("JOIGNABLES".equals(criteres.getFiltre())) {
                where.append(" and m.motif is null");
            } else if ("NON_JOIGNABLES".equals(criteres.getFiltre())) {
                where.append(" and m.motif is not null");
            }
        }
        if (criteres.getQ() != null && !criteres.getQ().isBlank()) {
            where.append(" and (c.raison_sociale like :q or c.code like :q or c.nom_pharmacien like :q)");
            filtres.put("q", "%" + criteres.getQ().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        String depuis = " from (" + source + ") m join crm_client c on c.id = m.clientId join crm_agence a on a.id = c.agence_id" + where;
        Query total = em.createNativeQuery("select count(*)" + depuis);
        filtres.forEach(total::setParameter);
        Query page = em.createNativeQuery("""
                select c.id as clientId, c.code as code, c.raison_sociale as raisonSociale, c.nom_pharmacien as nomPharmacien,
                       a.libelle as agenceLibelle, c.segmentation as segmentation, c.ville as ville, m.origine as origine, m.motif as motif,
                       c.email as email, c.desabonne_marketing_le as desabonneLe,
                       (select group_concat(n.numero_whatsapp order by n.rang, n.id separator ',') from crm_contact n
                        where n.client_id = c.id and n.statut = 'ACTIF' and n.fonction in (:fonctions)) as numeros
                """ + depuis + " order by c.raison_sociale, c.code", Tuple.class);
        filtres.forEach(page::setParameter);
        page.setFirstResult(criteres.getPage() * criteres.getTaille());
        page.setMaxResults(criteres.getTaille());
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = page.getResultList();
        apercu.setMembres(PageDTO.<MembreListeDTO>builder()
                .elements(lignes.stream().map(this::mapTupleToMembreDto).toList())
                .total(((Number) total.getSingleResult()).longValue())
                .page(criteres.getPage())
                .taille(criteres.getTaille())
                .build());
        return apercu;
    }

    /** « from crm_client c ... where ... » des clients de la liste, dans le perimetre du lecteur. */
    private String requeteMembres(Definition d, Map<String, Object> parametres) {
        StringBuilder sql = new StringBuilder("from crm_client c").append(perimetreSql(parametres, "c")).append(" and (");
        Long liste = d.id() == null ? -1L : d.id();
        parametres.put("liste", liste);
        sql.append("exists (select 1 from crm_liste_diffusion_client x where x.liste_id = :liste and x.client_id = c.id and x.actif = true and x.sens = 'AJOUT')");
        if (d.parCriteres()) {
            sql.append(" or (not exists (select 1 from crm_liste_diffusion_client x where x.liste_id = :liste and x.client_id = c.id")
                    .append(" and x.actif = true and x.sens = 'EXCLUSION')");
            if (!d.agenceIds().isEmpty()) {
                sql.append(" and c.agence_id in (:critAgences)");
                parametres.put("critAgences", d.agenceIds());
            }
            if (!d.sousAgences().isEmpty()) {
                sql.append(" and c.sous_agence in (:critSousAgences)");
                parametres.put("critSousAgences", d.sousAgences());
            }
            if (!d.villes().isEmpty()) {
                sql.append(" and c.ville in (:critVilles)");
                parametres.put("critVilles", d.villes());
            }
            if (!d.segments().isEmpty()) {
                sql.append(" and c.segmentation in (:critSegments)");
                parametres.put("critSegments", d.segments());
            }
            sql.append(")");
        }
        return sql.append(")").toString();
    }

    private String origine(Definition d) {
        return "case when exists (select 1 from crm_liste_diffusion_client x where x.liste_id = :liste and x.client_id = c.id"
                + " and x.actif = true and x.sens = 'AJOUT') then 'AJOUT' else 'CRITERES' end";
    }

    private String perimetreSql(Map<String, Object> parametres, String alias) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (perimetre.isVisionGlobale()) {
            return " where " + alias + ".statut = 'ACTIF'";
        }
        parametres.put("agencesVisibles", perimetre.getAgenceIds().isEmpty() ? List.of(-1L) : perimetre.getAgenceIds());
        return " where " + alias + ".statut = 'ACTIF' and " + alias + ".agence_id in (:agencesVisibles)";
    }

    // --- Controles -----------------------------------------------------------------------------

    private Definition verifier(Long id, ListeDiffusionDemandeDTO demande) {
        List<String> motifs = new ArrayList<>();
        Number homonymes = (Number) em.createNativeQuery("select count(*) from crm_liste_diffusion where upper(nom) = upper(:nom) and id <> :id")
                .setParameter("nom", demande.getNom().trim()).setParameter("id", id == null ? -1L : id).getSingleResult();
        if (homonymes.longValue() > 0) {
            motifs.add("Une liste « " + demande.getNom().trim() + " » existe déjà.");
        }
        Definition d = definition(id, demande);
        if (!d.agenceIds().isEmpty()) {
            Number connues = (Number) em.createNativeQuery("select count(*) from crm_agence where id in (:ids)")
                    .setParameter("ids", d.agenceIds()).getSingleResult();
            if (connues.intValue() != d.agenceIds().size()) {
                motifs.add("Agence inconnue.");
            }
        }
        if (!d.segments().isEmpty()) {
            @SuppressWarnings("unchecked")
            List<String> connus = em.createNativeQuery("select code from crm_segment where code in (:codes)").setParameter("codes", d.segments())
                    .getResultList();
            d.segments().stream().filter(s -> !connus.contains(s)).forEach(s -> motifs.add("Segment inconnu : " + s + "."));
        }
        d.fonctions().stream().filter(f -> !ImportClientServiceImpl.NUMEROS.containsKey(f))
                .forEach(f -> motifs.add("Type de numéro inconnu : " + f + "."));
        if (!motifs.isEmpty()) {
            throw new ErreurMetierException(String.join(" ", motifs));
        }
        return d;
    }

    private Definition definition(Long id, ListeDiffusionDemandeDTO demande) {
        List<String> fonctions = nettoyer(demande.getFonctions());
        return new Definition(id, demande.isParCriteres(),
                demande.getAgenceIds() == null ? List.of() : demande.getAgenceIds().stream().filter(Objects::nonNull).distinct().toList(),
                nettoyer(demande.getSousAgences()).stream().map(s -> s.toUpperCase(Locale.ROOT)).toList(),
                nettoyer(demande.getVilles()), nettoyer(demande.getSegments()),
                fonctions.isEmpty() ? List.of("PRINCIPAL") : fonctions);
    }

    private Definition definition(ListeDiffusionDTO l) {
        return new Definition(l.getId(), l.isParCriteres(), l.getAgenceIds(), l.getSousAgences(), l.getVilles(), l.getSegments(), l.getFonctions());
    }

    // --- Lecture -------------------------------------------------------------------------------

    private ListeDiffusionDTO changerStatut(Long id, String statut, String action) {
        ListeDiffusionDTO liste = consulter(id);
        em.createNativeQuery("update crm_liste_diffusion set statut = :statut, modifie_par = :par, updated_at = utc_timestamp() where id = :id")
                .setParameter("statut", statut).setParameter("par", perimetreService.perimetreCourant().getUtilisateurId())
                .setParameter("id", id).executeUpdate();
        journalActionService.enregistrer(action, "crm_liste_diffusion", id, liste.getNom());
        return consulter(id);
    }

    private ListeDiffusionDTO avecCompteurs(Tuple tuple) {
        ListeDiffusionDTO liste = mapTupleToDto(tuple);
        ApercuListeDTO apercu = simulerDefinition(definition(liste));
        liste.setNbClients(apercu.getNbClients());
        liste.setNbJoignables(apercu.getNbJoignables());
        return liste;
    }

    private ApercuListeDTO simulerDefinition(Definition d) {
        return apercu(d, MembresCriteresDTO.builder().taille(1).build());
    }

    ListeDiffusionDTO mapTupleToDto(Tuple tuple) {
        Object parCriteres = tuple.get("parCriteres");
        return ListeDiffusionDTO.builder()
                .id(tuple.get("id", Long.class))
                .nom(tuple.get("nom", String.class))
                .description(tuple.get("description", String.class))
                .parCriteres(parCriteres instanceof Boolean b ? b : parCriteres instanceof Number n && n.intValue() != 0)
                .agenceIds(liste(tuple.get("agenceIds", String.class)).stream().map(Long::valueOf).toList())
                .sousAgences(liste(tuple.get("sousAgences", String.class)))
                .villes(liste(tuple.get("villes", String.class)))
                .segments(liste(tuple.get("segments", String.class)))
                .fonctions(liste(tuple.get("fonctions", String.class)))
                .statut(tuple.get("statut", String.class))
                .nbAjouts(nombre(tuple.get("nbAjouts")))
                .nbExclusions(nombre(tuple.get("nbExclusions")))
                .creePar(tuple.get("creePar", String.class))
                .creeLe(tuple.get("creeLe", LocalDateTime.class))
                .modifieLe(tuple.get("modifieLe", LocalDateTime.class))
                .build();
    }

    MembreListeDTO mapTupleToMembreDto(Tuple tuple) {
        return MembreListeDTO.builder()
                .clientId(((Number) tuple.get("clientId")).longValue())
                .code(tuple.get("code", String.class))
                .raisonSociale(tuple.get("raisonSociale", String.class))
                .nomPharmacien(tuple.get("nomPharmacien", String.class))
                .agenceLibelle(tuple.get("agenceLibelle", String.class))
                .segmentation(tuple.get("segmentation", String.class))
                .ville(tuple.get("ville", String.class))
                .origine(tuple.get("origine", String.class))
                .motifExclusion(tuple.get("motif", String.class))
                .email(tuple.get("email", String.class))
                .desabonneLe(tuple.get("desabonneLe", LocalDateTime.class))
                .numeros(liste(tuple.get("numeros", String.class)))
                .build();
    }

    private List<String> distincts(String sql, PerimetreDTO perimetre) {
        Query requete = em.createNativeQuery(sql);
        if (!perimetre.isVisionGlobale()) {
            requete.setParameter("agences", perimetre.getAgenceIds().isEmpty() ? List.of(-1L) : perimetre.getAgenceIds());
        }
        @SuppressWarnings("unchecked")
        List<String> valeurs = requete.getResultList();
        return valeurs;
    }

    private static String resume(Definition d) {
        if (!d.parCriteres()) {
            return "clients ajoutés à la main ; numéros " + String.join(", ", d.fonctions());
        }
        List<String> parties = new ArrayList<>();
        if (!d.agenceIds().isEmpty()) parties.add("agences " + csv(d.agenceIds()));
        if (!d.sousAgences().isEmpty()) parties.add("sous-agences " + csv(d.sousAgences()));
        if (!d.villes().isEmpty()) parties.add("villes " + csv(d.villes()));
        if (!d.segments().isEmpty()) parties.add("segments " + csv(d.segments()));
        return (parties.isEmpty() ? "tous les clients" : String.join(" ; ", parties)) + " ; numéros " + String.join(", ", d.fonctions());
    }

    private static List<String> nettoyer(List<String> valeurs) {
        return valeurs == null ? List.of() : valeurs.stream().filter(Objects::nonNull).map(String::trim).filter(v -> !v.isEmpty()).distinct().toList();
    }

    private static List<String> liste(String csv) {
        return csv == null || csv.isBlank() ? List.of() : Arrays.stream(csv.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList();
    }

    private static String csv(List<?> valeurs) {
        return valeurs.isEmpty() ? null : valeurs.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static long nombre(Object valeur) {
        return valeur == null ? 0 : ((Number) valeur).longValue();
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
