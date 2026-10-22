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

import ci.ubipharm.crm.rest.service.ClientService;
import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.ReferentielService;
import ci.ubipharm.crm.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.rest.service.dto.LocalisationDTO;
import ci.ubipharm.crm.rest.service.dto.ClientCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.ClientDTO;
import ci.ubipharm.crm.rest.service.dto.ClientDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.SegmentDTO;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.PortefeuilleService;
import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
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
                   sg.badge as segmentationBadge, c.nom_pharmacien as nomPharmacien, c.statut as statut,
                   c.region_id as regionId, rg.libelle as region, c.ville_id as villeId, vi.libelle as ville,
                   c.commune_id as communeId, co.libelle as commune, c.tournee_id as tourneeId, tr.libelle as tournee,
                   c.date_anniversaire as dateAnniversaire, c.desactive_le as desactiveLe, ud.nom_complet as desactivePar,
                   c.desactive_motif as desactiveMotif,
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
            left join crm_referentiel rg on rg.id = c.region_id
            left join crm_referentiel vi on vi.id = c.ville_id
            left join crm_referentiel co on co.id = c.commune_id
            left join crm_referentiel tr on tr.id = c.tournee_id
            left join crm_utilisateur ud on ud.id = c.desactive_par
            """;

    private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final EntityManager em;
    private final PerimetreService perimetreService;
    private final PortefeuilleService portefeuilleService;
    private final ParametreService parametreService;
    private final ReferentielService referentielService;
    private final UtilisateurCourantService utilisateurCourantService;
    private final JournalActionService journalActionService;

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
        List<Long> portefeuille = portefeuille(perimetre);
        if (portefeuille != null) {
            where.append(" and exists (select 1 from crm_affectation af where af.client_id = c.id and af.date_fin is null and af.agent_id in (:portefeuille))");
            parametres.put("portefeuille", portefeuille);
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

    /**
     * Agents dont l'utilisateur voit seulement le portefeuille (lui et ceux qu'il remplace), ou null s'il voit ses agences :
     * sans le droit PERIMETRE_AGENCES, un agent du Recouvrement ne voit que ses clients (decision d'Airman du 22/10/2026).
     */
    private List<Long> portefeuille(PerimetreDTO perimetre) {
        return perimetre.isVisionGlobale() ? null : portefeuilleService.agentsGeres();
    }

    @Override
    @Transactional(readOnly = true)
    public ClientDTO consulter(Long id) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        List<Long> portefeuille = portefeuille(perimetre);
        String sql = SELECT + " where c.id = :id" + (perimetre.isVisionGlobale() ? "" : " and c.agence_id in (:agencesVisibles)")
                + (portefeuille == null ? "" : " and exists (select 1 from crm_affectation af where af.client_id = c.id and af.date_fin is null"
                        + " and af.agent_id in (:portefeuille))");
        Query requete = em.createNativeQuery(sql, Tuple.class).setParameter("id", id);
        if (portefeuille != null) {
            requete.setParameter("portefeuille", portefeuille);
        }
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
        ClientDTO client = mapTupleToDto(lignes.getFirst());
        client.setNumeros(numeros(id));
        return client;
    }

    @Override
    @Transactional
    public ClientDTO creer(ClientDemandeDTO demande) {
        String code = vide(demande.getCode());
        if (code == null) {
            throw new ErreurMetierException("Saisissez le numéro de compte du client.");
        }
        Number homonymes = (Number) em.createNativeQuery("select count(*) from crm_client where upper(code) = upper(:code)")
                .setParameter("code", code).getSingleResult();
        if (homonymes.longValue() > 0) {
            throw new ErreurMetierException("Le numéro de compte " + code + " existe déjà.");
        }
        Saisie saisie = verifier(demande, Set.of());
        localiser(em.createNativeQuery("""
                insert into crm_client (code, raison_sociale, nom_pharmacien, segmentation, agence_id, sous_agence, region_id, ville_id,
                                        commune_id, tournee_id, date_anniversaire, adresse, email, commentaire, statut, created_at)
                values (:code, :raisonSociale, :nomPharmacien, :segmentation, :agenceId, :sousAgence, :regionId, :villeId, :communeId,
                        :tourneeId, :dateAnniversaire, :adresse, :email, :commentaire, 'ACTIF', utc_timestamp())
                """), saisie)
                .setParameter("code", code)
                .setParameter("raisonSociale", saisie.raisonSociale())
                .setParameter("nomPharmacien", saisie.nomPharmacien())
                .setParameter("segmentation", saisie.segmentation())
                .setParameter("agenceId", demande.getAgenceId())
                .setParameter("sousAgence", saisie.sousAgence())
                .setParameter("adresse", saisie.adresse())
                .setParameter("email", saisie.email())
                .setParameter("commentaire", saisie.commentaire())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        saisie.numeros().forEach((fonction, numero) -> ajouterContact(id, fonction, numero, saisie.nomPharmacien()));
        journalActionService.enregistrer("CLIENT_CREATION", "crm_client", id, code + " " + saisie.raisonSociale());
        return consulter(id);
    }

    @Override
    @Transactional
    public ClientDTO modifier(Long id, ClientDemandeDTO demande) {
        ClientDTO avant = consulter(id);
        if (!"ACTIF".equals(avant.getStatut())) {
            throw new ErreurMetierException("ARCHIVE".equals(avant.getStatut()) ? "Ce client est archivé." : "Ce client est désactivé : réactivez-le d'abord.");
        }
        Set<Long> dejaPortes = new java.util.HashSet<>();
        java.util.stream.Stream.of(avant.getRegionId(), avant.getVilleId(), avant.getCommuneId(), avant.getTourneeId())
                .filter(Objects::nonNull).forEach(dejaPortes::add);
        Saisie saisie = verifier(demande, dejaPortes);
        localiser(em.createNativeQuery("""
                update crm_client set raison_sociale = :raisonSociale, nom_pharmacien = :nomPharmacien, segmentation = :segmentation,
                       agence_id = :agenceId, sous_agence = :sousAgence, region_id = :regionId, ville_id = :villeId,
                       commune_id = :communeId, tournee_id = :tourneeId, date_anniversaire = :dateAnniversaire, adresse = :adresse,
                       email = :email, commentaire = :commentaire, updated_at = utc_timestamp()
                where id = :id
                """), saisie)
                .setParameter("raisonSociale", saisie.raisonSociale())
                .setParameter("nomPharmacien", saisie.nomPharmacien())
                .setParameter("segmentation", saisie.segmentation())
                .setParameter("agenceId", demande.getAgenceId())
                .setParameter("sousAgence", saisie.sousAgence())
                .setParameter("adresse", saisie.adresse())
                .setParameter("email", saisie.email())
                .setParameter("commentaire", saisie.commentaire())
                .setParameter("id", id)
                .executeUpdate();

        List<String> ecarts = new ArrayList<>();
        ecart(ecarts, "Pharmacie", avant.getRaisonSociale(), saisie.raisonSociale());
        ecart(ecarts, "Pharmacien", avant.getNomPharmacien(), saisie.nomPharmacien());
        ecart(ecarts, "Agence", String.valueOf(avant.getAgenceId()), String.valueOf(demande.getAgenceId()));
        ecart(ecarts, "Sous-agence", avant.getSousAgence(), saisie.sousAgence());
        ecart(ecarts, "Segment", avant.getSegmentation(), saisie.segmentation());
        ecart(ecarts, "Région", avant.getRegion(), saisie.localisation().getRegion());
        ecart(ecarts, "Ville", avant.getVille(), saisie.localisation().getVille());
        ecart(ecarts, "Commune", avant.getCommune(), saisie.localisation().getCommune());
        ecart(ecarts, "Tournée", avant.getTournee(), saisie.localisation().getTournee());
        ecart(ecarts, "Anniversaire", avant.getDateAnniversaire() == null ? null : avant.getDateAnniversaire().toString(),
                saisie.dateAnniversaire() == null ? null : saisie.dateAnniversaire().toString());
        ecart(ecarts, "Adresse", avant.getAdresse(), saisie.adresse());
        ecart(ecarts, "E-mail", avant.getEmail(), saisie.email());
        ecart(ecarts, "Commentaire", avant.getCommentaire(), saisie.commentaire());
        for (String fonction : ImportClientServiceImpl.NUMEROS.keySet()) {
            String actuel = avant.getNumeros().get(fonction);
            String nouveau = saisie.numeros().get(fonction);
            if (Objects.equals(actuel, nouveau)) {
                continue;
            }
            // L'ancien numero est archive : l'historique des envois y reste rattache
            em.createNativeQuery("update crm_contact set statut = 'ARCHIVE', updated_at = utc_timestamp() where client_id = :id and fonction = :fonction and statut = 'ACTIF'")
                    .setParameter("id", id).setParameter("fonction", fonction).executeUpdate();
            if (nouveau != null) {
                ajouterContact(id, fonction, nouveau, saisie.nomPharmacien());
            }
            ecart(ecarts, ImportClientServiceImpl.LIBELLES_NUMEROS.get(fonction), actuel, nouveau);
        }
        if (!ecarts.isEmpty()) {
            journalActionService.enregistrer("CLIENT_MODIFICATION", "crm_client", id, avant.getCode() + " : " + String.join(" ; ", ecarts));
        }
        return consulter(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SegmentDTO> segments() {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("select code as code, libelle as libelle, badge as badge from crm_segment where statut = 'ACTIF' order by ordre, code",
                Tuple.class).getResultList();
        return lignes.stream().map(t -> SegmentDTO.builder().code(t.get("code", String.class)).libelle(t.get("libelle", String.class))
                .badge(t.get("badge", String.class)).build()).toList();
    }

    @Override
    @Transactional
    public ClientDTO desactiver(Long id, String motif) {
        ClientDTO client = consulter(id);
        if (!"ACTIF".equals(client.getStatut())) {
            throw new ErreurMetierException("Seul un client actif se désactive.");
        }
        em.createNativeQuery("""
                update crm_client set statut = 'INACTIF', desactive_le = utc_timestamp(), desactive_par = :par, desactive_motif = :motif,
                       updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("par", utilisateurCourantService.utilisateur().getId())
                .setParameter("motif", motif.trim())
                .setParameter("id", id)
                .executeUpdate();
        journalActionService.enregistrer("CLIENT_DESACTIVATION", "crm_client", id, client.getCode() + " : " + motif.trim());
        return consulter(id);
    }

    @Override
    @Transactional
    public ClientDTO reactiver(Long id) {
        ClientDTO client = consulter(id);
        if (!"INACTIF".equals(client.getStatut())) {
            throw new ErreurMetierException("Ce client n'est pas désactivé.");
        }
        em.createNativeQuery("""
                update crm_client set statut = 'ACTIF', desactive_le = null, desactive_par = null, desactive_motif = null,
                       updated_at = utc_timestamp()
                where id = :id
                """).setParameter("id", id).executeUpdate();
        journalActionService.enregistrer("CLIENT_REACTIVATION", "crm_client", id, client.getCode() + " (désactivé le "
                + client.getDesactiveLe().toLocalDate() + " : " + client.getDesactiveMotif() + ")");
        return consulter(id);
    }

    private record Saisie(String raisonSociale, String nomPharmacien, String segmentation, String sousAgence, LocalisationDTO localisation,
                          java.time.LocalDate dateAnniversaire, String adresse, String email, String commentaire, Map<String, String> numeros) {
    }

    private Query localiser(Query requete, Saisie saisie) {
        return requete.setParameter("regionId", saisie.localisation().getRegionId())
                .setParameter("villeId", saisie.localisation().getVilleId())
                .setParameter("communeId", saisie.localisation().getCommuneId())
                .setParameter("tourneeId", saisie.localisation().getTourneeId())
                .setParameter("dateAnniversaire", saisie.dateAnniversaire());
    }

    /** Memes controles que l'import : agence du perimetre, segment connu, numeros a 10 chiffres, e-mail valide. */
    private Saisie verifier(ClientDemandeDTO demande, Set<Long> dejaPortes) {
        List<String> motifs = new ArrayList<>();
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        Number agence = (Number) em.createNativeQuery("select count(*) from crm_agence where id = :id and statut = 'ACTIF'")
                .setParameter("id", demande.getAgenceId()).getSingleResult();
        if (agence.longValue() == 0) {
            motifs.add("Agence inconnue.");
        } else if (!perimetre.isVisionGlobale() && !perimetre.getAgenceIds().contains(demande.getAgenceId())) {
            motifs.add("Cette agence est hors de votre périmètre.");
        }
        String segment = vide(demande.getSegmentation());
        if (segment != null && segments().stream().noneMatch(sg -> sg.getCode().equals(segment))) {
            motifs.add("Segment inconnu : " + segment + ".");
        }
        LocalisationDTO localisation = referentielService.resolution().parIdentifiants(demande.getRegionId(), demande.getVilleId(),
                demande.getCommuneId(), demande.getTourneeId(), demande.getAgenceId(), dejaPortes, motifs);
        java.time.LocalDate anniversaire = demande.getDateAnniversaire();
        if (anniversaire != null && (anniversaire.isAfter(java.time.LocalDate.now()) || anniversaire.getYear() < 1900)) {
            motifs.add("Date d'anniversaire invalide.");
        }
        String email = vide(demande.getEmail());
        if (email != null && !EMAIL.matcher(email).matches()) {
            motifs.add("Adresse e-mail invalide : " + email + ".");
        }
        String prefixe = parametreService.valeur("whatsapp.prefixe_pays", "225");
        Map<String, String> numeros = new LinkedHashMap<>();
        Map<String, String> saisis = demande.getNumeros() == null ? Map.of() : demande.getNumeros();
        saisis.keySet().stream().filter(f -> !ImportClientServiceImpl.NUMEROS.containsKey(f)).findFirst()
                .ifPresent(f -> motifs.add("Type de numéro inconnu : " + f + "."));
        ImportClientServiceImpl.NUMEROS.keySet().forEach(fonction -> {
            String saisi = vide(saisis.get(fonction));
            if (saisi != null) {
                ValeursImport.numeroWhatsapp(saisi, prefixe).ifPresentOrElse(n -> numeros.put(fonction, n), () -> motifs.add(
                        ImportClientServiceImpl.LIBELLES_NUMEROS.get(fonction) + " invalide : " + saisi + " (10 chiffres attendus, par exemple 07 00 00 00 01)."));
            }
        });
        if (!motifs.isEmpty()) {
            throw new ErreurMetierException(String.join(" ", motifs));
        }
        return new Saisie(demande.getRaisonSociale().trim(), vide(demande.getNomPharmacien()), segment,
                vide(demande.getSousAgence()) == null ? null : demande.getSousAgence().trim().toUpperCase(Locale.ROOT), localisation, anniversaire,
                vide(demande.getAdresse()), email == null ? null : email.toLowerCase(Locale.ROOT), vide(demande.getCommentaire()), numeros);
    }

    /** Rang 1 : numero de relance ; rang 2 : autres interlocuteurs ; le nom du pharmacien va au titulaire (comme a l'import). */
    private void ajouterContact(Long clientId, String fonction, String numero, String nomPharmacien) {
        em.createNativeQuery("""
                insert into crm_contact (client_id, nom, fonction, numero_whatsapp, rang, statut, created_at)
                values (:clientId, :nom, :fonction, :numero, :rang, 'ACTIF', utc_timestamp())
                """)
                .setParameter("clientId", clientId)
                .setParameter("nom", "TITULAIRE".equals(fonction) ? nomPharmacien : null)
                .setParameter("fonction", fonction)
                .setParameter("numero", numero)
                .setParameter("rang", "PRINCIPAL".equals(fonction) ? 1 : 2)
                .executeUpdate();
    }

    private Map<String, String> numeros(Long clientId) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select fonction as fonction, numero_whatsapp as numero from crm_contact
                where client_id = :id and statut = 'ACTIF' and fonction is not null order by id
                """, Tuple.class).setParameter("id", clientId).getResultList();
        Map<String, String> numeros = new LinkedHashMap<>();
        lignes.forEach(t -> numeros.put(t.get("fonction", String.class), t.get("numero", String.class)));
        return numeros;
    }

    private static void ecart(List<String> ecarts, String champ, String avant, String apres) {
        if (!Objects.equals(vide(avant), vide(apres))) {
            ecarts.add(champ + " : " + (vide(avant) == null ? "vide" : avant) + " -> " + (vide(apres) == null ? "vide" : apres));
        }
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    ClientDTO mapTupleToDto(Tuple tuple) {
        return ClientDTO.builder()
                .id(tuple.get("id", Long.class))
                .code(tuple.get("code", String.class))
                .raisonSociale(tuple.get("raisonSociale", String.class))
                .segmentation(tuple.get("segmentation", String.class))
                .segmentationLibelle(tuple.get("segmentationLibelle", String.class))
                .nomPharmacien(tuple.get("nomPharmacien", String.class))
                .segmentationBadge(tuple.get("segmentationBadge", String.class))
                .regionId(tuple.get("regionId") == null ? null : ((Number) tuple.get("regionId")).longValue())
                .region(tuple.get("region", String.class))
                .villeId(tuple.get("villeId") == null ? null : ((Number) tuple.get("villeId")).longValue())
                .ville(tuple.get("ville", String.class))
                .communeId(tuple.get("communeId") == null ? null : ((Number) tuple.get("communeId")).longValue())
                .commune(tuple.get("commune", String.class))
                .tourneeId(tuple.get("tourneeId") == null ? null : ((Number) tuple.get("tourneeId")).longValue())
                .tournee(tuple.get("tournee", String.class))
                .dateAnniversaire(tuple.get("dateAnniversaire", java.time.LocalDate.class))
                .desactiveLe(tuple.get("desactiveLe", java.time.LocalDateTime.class))
                .desactivePar(tuple.get("desactivePar", String.class))
                .desactiveMotif(tuple.get("desactiveMotif", String.class))
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
