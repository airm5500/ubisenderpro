package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import ci.ubipharm.crm.rest.service.EnvoiMasseService;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.LecteurTableurService;
import ci.ubipharm.crm.rest.service.ListeDiffusionService;
import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.PieceJointeService;
import ci.ubipharm.crm.rest.service.VariableMessageService;
import ci.ubipharm.crm.rest.service.dto.DestinataireMasseDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiMasseApercuDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiMasseDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiMasseDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.LectureDestinatairesDTO;
import ci.ubipharm.crm.rest.service.dto.LigneTableurDTO;
import ci.ubipharm.crm.rest.service.dto.ListeDiffusionDTO;
import ci.ubipharm.crm.rest.service.dto.ModeleMessageDTO;
import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.rest.service.dto.PieceJointeDTO;
import ci.ubipharm.crm.rest.service.dto.ReferencesEnvoiMasseDTO;
import ci.ubipharm.crm.rest.service.dto.ResultatEnvoiDTO;
import ci.ubipharm.crm.rest.service.dto.TableurDTO;
import ci.ubipharm.crm.rest.service.dto.VariableMessageDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EnvoiMasseServiceImpl implements EnvoiMasseService {

    private static final String SELECT = """
            select e.id as id, e.nom as nom, e.numero_expediteur as numeroExpediteur, e.contenu as contenu, e.statut as statut,
                   e.nb_destinataires as nbDestinataires, e.nb_exclus as nbExclus, c.nom_complet as creePar, e.created_at as creeLe,
                   a.nom_complet as annulePar, e.termine_le as termineLe, e.modele_id as modeleId, m.nom as modeleNom, e.depart_le as departLe,
                   (select count(*) from crm_message_sortant s where s.envoi_masse_id = e.id and s.statut in ('A_ENVOYER', 'ENVOI')) as nbEnAttente,
                   (select count(*) from crm_message_sortant s where s.envoi_masse_id = e.id and s.statut in ('ENVOYE', 'DISTRIBUE', 'LU')) as nbEnvoyes,
                   (select count(*) from crm_message_sortant s where s.envoi_masse_id = e.id and s.statut in ('DISTRIBUE', 'LU')) as nbDistribues,
                   (select count(*) from crm_message_sortant s where s.envoi_masse_id = e.id and s.statut = 'LU') as nbLus,
                   (select count(*) from crm_message_sortant s where s.envoi_masse_id = e.id and s.statut = 'ECHEC') as nbEchecs
            from crm_envoi_masse e join crm_utilisateur c on c.id = e.cree_par left join crm_utilisateur a on a.id = e.annule_par
            left join crm_modele_message m on m.id = e.modele_id
            """;

    /** Variables que l'envoi en masse sait remplir : colonnes du client et valeurs de l'entreprise ou de l'expediteur. */
    private static final Set<String> SOURCES_CALCULEES = Set.of("calcul.agence", "calcul.segment", "calcul.expediteur_nom", "calcul.expediteur_numero",
            "calcul.site_entreprise", "calcul.lien_commande", "calcul.telephone_entreprise", "calcul.nom_entreprise");
    private static final Pattern COLONNE = Pattern.compile("[a-z][a-z0-9_]*");
    private static final int PIECES_MAX = 10;
    /** Au-dela, WhatsApp coupe la legende d'une image : le texte part alors a part. */
    private static final int LEGENDE_MAX = 1000;

    /** Destinataire lu dans la saisie ou la liste. */
    private record Destinataire(String saisi, String numero, String nom, Long clientId, String motif) {
    }

    private final EntityManager em;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PerimetreService perimetreService;
    private final ParametreService parametreService;
    private final ListeDiffusionService listeDiffusionService;
    private final PasserelleWhatsappService passerelle;
    private final JournalActionService journalActionService;
    private final CadenceWhatsapp cadence;
    private final PieceJointeService pieceJointeService;
    private final VariableMessageService variableMessageService;
    private final LecteurTableurService lecteurTableurService;

    @Override
    @Transactional
    public EnvoiMasseApercuDTO creer(EnvoiMasseDemandeDTO demande) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        @SuppressWarnings("unchecked")
        List<Object[]> moi = em.createNativeQuery("""
                select u.numero_whatsapp, s.fournisseur from crm_utilisateur u left join crm_session_whatsapp s on s.numero = u.numero_whatsapp
                where u.id = :id
                """).setParameter("id", perimetre.getUtilisateurId()).getResultList();
        String expediteur = moi.isEmpty() ? null : (String) moi.getFirst()[0];
        if (expediteur == null) {
            throw new ErreurMetierException("Votre compte n'a pas de numéro WhatsApp : l'administrateur doit le renseigner, puis reliez le téléphone.");
        }
        if ("META".equals(moi.getFirst()[1])) {
            throw new ErreurMetierException("Votre numéro passe par l'API Meta, qui n'accepte pas un message libre en masse : utilisez une campagne avec un modèle approuvé.");
        }
        if ((demande.getNumeros() == null || demande.getNumeros().isBlank()) && demande.getListeId() == null
                && (demande.getClientIds() == null || demande.getClientIds().isEmpty())) {
            throw new ErreurMetierException("Ajoutez des destinataires : clients choisis, liste de diffusion, fichier importé ou numéros collés.");
        }
        verifierModele(demande.getModeleId());
        List<Long> pieces = demande.getPieceJointeIds() == null ? List.of() : demande.getPieceJointeIds().stream().distinct().toList();
        if (pieces.size() > PIECES_MAX) {
            throw new ErreurMetierException("Un envoi en masse porte au plus " + PIECES_MAX + " pièces jointes.");
        }
        List<PieceJointeDTO> decrites = pieces.stream().map(pieceJointeService::decrire).toList();
        LocalDateTime depart = demande.getDepartLe() == null || !demande.getDepartLe().isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC))
                ? null : demande.getDepartLe();
        List<Destinataire> destinataires = destinataires(demande, perimetre);
        int max = parametreService.entier("whatsapp.envoi_masse.destinataires_max", 1000);
        List<Destinataire> retenus = destinataires.stream().filter(d -> d.motif() == null).toList();
        if (retenus.size() > max) {
            throw new ErreurMetierException(retenus.size() + " destinataires : un envoi en masse en compte au plus " + max + " (Paramètres).");
        }
        List<DestinataireMasseDTO> exclus = destinataires.stream().filter(d -> d.motif() != null)
                .map(d -> DestinataireMasseDTO.builder().saisi(d.saisi()).numero(d.numero()).nom(d.nom()).clientId(d.clientId()).statut("EXCLU").motif(d.motif()).build())
                .toList();
        Personnalisation textes = new Personnalisation(demande.getContenu(), destinataires, expediteur);
        String exemple = retenus.isEmpty() ? null : textes.rendre(retenus.getFirst());
        if (demande.isApercu()) {
            return EnvoiMasseApercuDTO.builder().nbRetenus(retenus.size()).exclus(exclus).exemple(exemple).piecesJointes(decrites)
                    .variablesRetirees(textes.retirees).horsPlage(!cadence.dansLaPlageEnvoiMasse()).plageHoraire(cadence.plageEnvoiMasse()).build();
        }
        if (retenus.isEmpty()) {
            throw new ErreurMetierException("Aucun destinataire à qui envoyer : tous sont exclus.");
        }
        em.createNativeQuery("""
                insert into crm_envoi_masse (nom, numero_expediteur, contenu, modele_id, depart_le, statut, nb_destinataires, nb_exclus, cree_par, created_at)
                values (:nom, :expediteur, :contenu, :modele, :depart, 'EN_COURS', :nb, :exclus, :par, utc_timestamp())
                """).setParameter("nom", demande.getNom().trim()).setParameter("expediteur", expediteur).setParameter("contenu", demande.getContenu().strip())
                .setParameter("modele", demande.getModeleId()).setParameter("depart", depart)
                .setParameter("nb", retenus.size()).setParameter("exclus", exclus.size()).setParameter("par", perimetre.getUtilisateurId()).executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        for (int i = 0; i < pieces.size(); i++) {
            em.createNativeQuery("insert into crm_envoi_masse_piece_jointe (envoi_masse_id, piece_jointe_id, ordre) values (:envoi, :piece, :ordre)")
                    .setParameter("envoi", id).setParameter("piece", pieces.get(i)).setParameter("ordre", i).executeUpdate();
        }
        for (Destinataire d : destinataires) {
            if (d.numero() == null) {
                continue;
            }
            em.createNativeQuery("""
                    insert ignore into crm_message_sortant (origine, envoi_masse_id, numero_expediteur, numero_destinataire, nom_destinataire, client_id,
                                                            contenu, statut, motif, cree_par, created_at)
                    values ('ENVOI_MASSE', :envoi, :expediteur, :numero, :nom, :client, :contenu, :statut, :motif, :par, utc_timestamp())
                    """).setParameter("envoi", id).setParameter("expediteur", expediteur).setParameter("numero", d.numero()).setParameter("nom", d.nom())
                    .setParameter("client", d.clientId()).setParameter("contenu", d.motif() == null ? textes.rendre(d) : demande.getContenu().strip())
                    .setParameter("statut", d.motif() == null ? "A_ENVOYER" : "EXCLU").setParameter("motif", d.motif())
                    .setParameter("par", perimetre.getUtilisateurId()).executeUpdate();
        }
        journalActionService.enregistrer("ENVOI_MASSE_CREATION", "crm_envoi_masse", id,
                demande.getNom().trim() + " : " + retenus.size() + " destinataire(s), " + exclus.size() + " exclu(s), depuis " + expediteur
                        + (pieces.isEmpty() ? "" : ", " + pieces.size() + " pièce(s) jointe(s)") + (depart == null ? "" : ", départ le " + depart + " UTC"));
        return EnvoiMasseApercuDTO.builder().envoi(consulter(id)).nbRetenus(retenus.size()).exclus(exclus).exemple(exemple).piecesJointes(decrites)
                .horsPlage(!cadence.dansLaPlageEnvoiMasse()).plageHoraire(cadence.plageEnvoiMasse()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageDTO<EnvoiMasseDTO> rechercher(int page, int taille) {
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        String where = perimetre.isVisionGlobale() ? "" : " where e.cree_par = :moi";
        Query comptage = em.createNativeQuery("select count(*) from crm_envoi_masse e" + where);
        Query requete = em.createNativeQuery(SELECT + where + " order by e.created_at desc, e.id desc", Tuple.class);
        if (!perimetre.isVisionGlobale()) {
            comptage.setParameter("moi", perimetre.getUtilisateurId());
            requete.setParameter("moi", perimetre.getUtilisateurId());
        }
        requete.setFirstResult(page * taille);
        requete.setMaxResults(taille);
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return PageDTO.<EnvoiMasseDTO>builder().elements(lignes.stream().map(this::mapTupleToDto).toList())
                .total(((Number) comptage.getSingleResult()).longValue()).page(page).taille(taille).build();
    }

    @Override
    @Transactional(readOnly = true)
    public EnvoiMasseDTO consulter(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where e.id = :id", Tuple.class).setParameter("id", id).getResultList();
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        if (lignes.isEmpty() || !perimetre.isVisionGlobale()
                && !perimetre.getUtilisateurId().equals(((Number) em.createNativeQuery("select cree_par from crm_envoi_masse where id = :id")
                        .setParameter("id", id).getSingleResult()).longValue())) {
            throw new RessourceIntrouvableException("Envoi introuvable.");
        }
        EnvoiMasseDTO envoi = mapTupleToDto(lignes.getFirst());
        envoi.setPiecesJointes(jdbc.queryForList("select piece_jointe_id from crm_envoi_masse_piece_jointe where envoi_masse_id = :id order by ordre",
                Map.of("id", id), Long.class).stream().map(pieceJointeService::decrire).toList());
        envoi.setHorsPlage(!cadence.dansLaPlageEnvoiMasse());
        envoi.setPlageHoraire(cadence.plageEnvoiMasse());
        return envoi;
    }

    @Override
    @Transactional(readOnly = true)
    public PageDTO<DestinataireMasseDTO> destinataires(Long id, String statut, int page, int taille) {
        consulter(id);
        String where = " where s.envoi_masse_id = :id" + (statut == null || statut.isBlank() ? "" : " and s.statut = :statut");
        Query comptage = em.createNativeQuery("select count(*) from crm_message_sortant s" + where).setParameter("id", id);
        Query requete = em.createNativeQuery("""
                select s.id as id, s.numero_destinataire as numero, s.nom_destinataire as nom, s.client_id as clientId, s.statut as statut,
                       s.motif as motif, s.envoye_le as envoyeLe
                from crm_message_sortant s""" + where + " order by s.id", Tuple.class).setParameter("id", id);
        if (statut != null && !statut.isBlank()) {
            comptage.setParameter("statut", statut);
            requete.setParameter("statut", statut);
        }
        requete.setFirstResult(page * taille);
        requete.setMaxResults(taille);
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        return PageDTO.<DestinataireMasseDTO>builder()
                .elements(lignes.stream().map(t -> DestinataireMasseDTO.builder()
                        .id(t.get("id", Long.class)).numero(t.get("numero", String.class)).nom(t.get("nom", String.class))
                        .clientId(t.get("clientId", Long.class)).statut(t.get("statut", String.class)).motif(t.get("motif", String.class))
                        .envoyeLe(t.get("envoyeLe", LocalDateTime.class)).build()).toList())
                .total(((Number) comptage.getSingleResult()).longValue()).page(page).taille(taille).build();
    }

    @Override
    @Transactional
    public EnvoiMasseDTO annuler(Long id) {
        EnvoiMasseDTO envoi = consulter(id);
        if (!"EN_COURS".equals(envoi.getStatut())) {
            throw new ErreurMetierException("Cet envoi n'est plus en cours.");
        }
        int annules = em.createNativeQuery("""
                update crm_message_sortant set statut = 'ANNULE', updated_at = utc_timestamp() where envoi_masse_id = :id and statut = 'A_ENVOYER'
                """).setParameter("id", id).executeUpdate();
        em.createNativeQuery("""
                update crm_envoi_masse set statut = 'ANNULE', annule_par = :par, termine_le = utc_timestamp(), updated_at = utc_timestamp() where id = :id
                """).setParameter("id", id).setParameter("par", perimetreService.perimetreCourant().getUtilisateurId()).executeUpdate();
        journalActionService.enregistrer("ENVOI_MASSE_ANNULATION", "crm_envoi_masse", id, envoi.getNom() + " : " + annules + " message(s) non envoyé(s)");
        return consulter(id);
    }

    @Override
    public int envoyerLot() {
        if (!cadence.dansLaPlageEnvoiMasse()) {
            return 0;
        }
        // Pendant la plage des relances, les relances et promotions d'un numero passent avant ses envois en masse ;
        // en dehors, elles attendent et ne retiennent pas les envois en masse
        String prioriteCampagnes = !cadence.dansLaPlage() ? "" : """
                 and not exists (
                    select 1 from crm_campagne_message m join crm_campagne c on c.id = m.campagne_id
                    where m.numero_expediteur = s.numero_expediteur and m.statut = 'A_ENVOYER' and c.statut = 'EN_COURS')""";
        List<String> numeros = jdbc.queryForList("""
                select distinct s.numero_expediteur from crm_message_sortant s
                join crm_envoi_masse e on e.id = s.envoi_masse_id and e.statut = 'EN_COURS' and (e.depart_le is null or e.depart_le <= utc_timestamp())
                join crm_session_whatsapp w on w.numero = s.numero_expediteur and w.statut = 'CONNECTE' and w.fournisseur = 'WA_WEB'
                where s.statut = 'A_ENVOYER'""" + prioriteCampagnes, Map.of(), String.class);
        int envoyes = 0;
        for (String numero : numeros) {
            if (!cadence.peutEnvoyer(numero)) {
                continue;
            }
            Map<String, Object> message = reserver(numero, null);
            if (message == null) {
                continue;
            }
            ResultatEnvoiDTO resultat = envoyer(numero, (String) message.get("numero_destinataire"), (String) message.get("contenu"),
                    pieceJointeService.piecesDEnvoiMasse(((Number) message.get("envoi_masse_id")).longValue()));
            transaction.executeWithoutResult(statut -> enregistrer(message, resultat));
            cadence.envoye(numero);
            envoyes++;
        }
        return envoyes;
    }

    @Override
    @Transactional(readOnly = true)
    public ReferencesEnvoiMasseDTO references() {
        List<ListeDiffusionDTO> listes = listeDiffusionService.lister(false).stream().filter(l -> "ACTIF".equals(l.getStatut())).toList();
        @SuppressWarnings("unchecked")
        List<Tuple> modeles = em.createNativeQuery("""
                select id, nom, type_campagne as typeCampagne, type_information as typeInformation, contenu from crm_modele_message
                where module = 'MARKETING' and canal = 'WHATSAPP' and statut = 'ACTIF' order by type_campagne, nom
                """, Tuple.class).getResultList();
        List<VariableMessageDTO> variables = new ArrayList<>();
        variables.add(VariableMessageDTO.builder().code("nom").libelle("Nom saisi à côté du numéro, sinon celui du client").build());
        variableMessageService.variables().stream().filter(v -> remplissable(v.getSource())).forEach(variables::add);
        return ReferencesEnvoiMasseDTO.builder().listes(listes)
                .modeles(modeles.stream().map(t -> ModeleMessageDTO.builder().id(t.get("id", Long.class)).nom(t.get("nom", String.class))
                        .typeCampagne(t.get("typeCampagne", String.class)).typeInformation(t.get("typeInformation", String.class))
                        .contenu(t.get("contenu", String.class)).build()).toList())
                .variables(variables).build();
    }

    @Override
    public LectureDestinatairesDTO lireFichier(MultipartFile fichier) {
        TableurDTO tableur = lecteurTableurService.lire(fichier);
        List<String> entetes = tableur.getEntetes();
        String colonneNumero = entetes.stream().filter(e -> e.matches(".*(numero|telephone|whatsapp|mobile|portable|contact|tel).*")).findFirst()
                .orElse(entetes.getFirst());
        String colonneNom = entetes.stream().filter(e -> !e.equals(colonneNumero) && e.matches(".*(nom|raison|client|pharmacie|societe).*")).findFirst()
                .orElse(entetes.stream().filter(e -> !e.equals(colonneNumero)).findFirst().orElse(null));
        String prefixe = parametreService.valeur("whatsapp.prefixe_pays", "225");
        List<DestinataireMasseDTO> valides = new ArrayList<>();
        List<DestinataireMasseDTO> invalides = new ArrayList<>();
        for (LigneTableurDTO ligne : tableur.getLignes()) {
            String saisi = ligne.getValeurs().getOrDefault(colonneNumero, "").trim();
            String nom = colonneNom == null ? null : ligne.getValeurs().getOrDefault(colonneNom, "").trim();
            nom = nom == null || nom.isBlank() ? null : nom;
            if (saisi.isEmpty() && nom == null) {
                continue;
            }
            Optional<String> numero = ValeursImport.numeroWhatsapp(saisi, prefixe);
            DestinataireMasseDTO d = DestinataireMasseDTO.builder().saisi(saisi).numero(numero.orElse(null)).nom(nom)
                    .motif(numero.isPresent() ? null : saisi.isEmpty() ? "Ligne " + ligne.getNumero() + " : numéro manquant"
                            : "Ligne " + ligne.getNumero() + " : numéro non reconnu")
                    .build();
            (numero.isPresent() ? valides : invalides).add(d);
        }
        return LectureDestinatairesDTO.builder().valides(valides).invalides(invalides).build();
    }

    @Override
    public PieceJointeDTO deposerPieceJointe(MultipartFile fichier) {
        return pieceJointeService.deposerDocument(fichier);
    }

    @Override
    @Transactional(readOnly = true)
    public FichierDTO pieceJointe(Long id, Long pieceId) {
        consulter(id);
        if (jdbc.queryForObject("select count(*) from crm_envoi_masse_piece_jointe where envoi_masse_id = :id and piece_jointe_id = :piece",
                Map.of("id", id, "piece", pieceId), Integer.class) == 0) {
            throw new RessourceIntrouvableException("Pièce jointe introuvable");
        }
        return pieceJointeService.lire(pieceId);
    }

    @Override
    @Transactional
    public EnvoiMasseDTO relancerEchecs(Long id) {
        EnvoiMasseDTO envoi = consulter(id);
        if ("ANNULE".equals(envoi.getStatut())) {
            throw new ErreurMetierException("Cet envoi a été annulé : créez-en un nouveau.");
        }
        int relances = em.createNativeQuery("""
                update crm_message_sortant set statut = 'A_ENVOYER', tentatives = 0, motif = null, updated_at = utc_timestamp()
                where envoi_masse_id = :id and statut = 'ECHEC'
                """).setParameter("id", id).executeUpdate();
        if (relances == 0) {
            throw new ErreurMetierException("Aucun message en échec à renvoyer.");
        }
        em.createNativeQuery("update crm_envoi_masse set statut = 'EN_COURS', termine_le = null, updated_at = utc_timestamp() where id = :id")
                .setParameter("id", id).executeUpdate();
        journalActionService.enregistrer("ENVOI_MASSE_RELANCE", "crm_envoi_masse", id, envoi.getNom() + " : " + relances + " échec(s) remis en attente");
        return consulter(id);
    }

    @Override
    public int reprendreEnvoisInterrompus() {
        return transaction.execute(statut -> jdbc.update("""
                update crm_message_sortant set statut = 'ECHEC', updated_at = utc_timestamp(),
                       motif = 'Envoi interrompu : verifiez dans le WhatsApp de l''expediteur avant de renvoyer'
                where statut = 'ENVOI'
                """, Map.of()));
    }

    @Override
    public boolean envoyerMaintenant(Long messageId) {
        Map<String, Object> message = reserver(null, messageId);
        if (message == null) {
            return false;
        }
        String numero = (String) message.get("numero_expediteur");
        ResultatEnvoiDTO resultat = envoyer(numero, (String) message.get("numero_destinataire"), (String) message.get("contenu"),
                pieceJointeService.piecesDEnvoiMasse(((Number) message.get("envoi_masse_id")).longValue()));
        transaction.executeWithoutResult(statut -> enregistrer(message, resultat));
        cadence.envoye(numero);
        return resultat.isSucces();
    }

    private Map<String, Object> reserver(String numero, Long id) {
        return transaction.execute(statut -> {
            List<Map<String, Object>> messages = jdbc.queryForList("""
                    select s.id, s.envoi_masse_id, s.numero_expediteur, s.numero_destinataire, s.contenu, s.tentatives
                    from crm_message_sortant s join crm_envoi_masse e on e.id = s.envoi_masse_id
                    where\s""" + (id == null ? """
                    s.numero_expediteur = :numero and (e.depart_le is null or e.depart_le <= utc_timestamp())""" : "s.id = :id") + """
                     and s.statut = 'A_ENVOYER' and e.statut = 'EN_COURS'
                    order by e.created_at, s.id limit 1 for update
                    """, id == null ? Map.of("numero", numero) : Map.of("id", id));
            if (messages.isEmpty()) {
                return null;
            }
            jdbc.update("update crm_message_sortant set statut = 'ENVOI', updated_at = utc_timestamp() where id = :id", Map.of("id", messages.getFirst().get("id")));
            return messages.getFirst();
        });
    }

    private void enregistrer(Map<String, Object> message, ResultatEnvoiDTO resultat) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", message.get("id")).addValue("identifiant", resultat.getIdentifiant())
                .addValue("motif", resultat.getErreur() == null ? null : resultat.getErreur().substring(0, Math.min(255, resultat.getErreur().length())));
        if (resultat.isSucces()) {
            jdbc.update("""
                    update crm_message_sortant set statut = 'ENVOYE', identifiant_whatsapp = :identifiant, envoye_le = utc_timestamp(),
                           tentatives = tentatives + 1, motif = :motif, updated_at = utc_timestamp() where id = :id
                    """, p);
        } else if (resultat.isSessionIndisponible()) {
            jdbc.update("update crm_message_sortant set statut = 'A_ENVOYER', updated_at = utc_timestamp() where id = :id", p);
        } else if (resultat.isErreurTechnique() && ((Number) message.get("tentatives")).intValue() + 1 < parametreService.entier("whatsapp.envoi.tentatives_max", 3)) {
            jdbc.update("update crm_message_sortant set statut = 'A_ENVOYER', tentatives = tentatives + 1, motif = :motif, updated_at = utc_timestamp() where id = :id", p);
        } else {
            jdbc.update("update crm_message_sortant set statut = 'ECHEC', tentatives = tentatives + 1, motif = :motif, updated_at = utc_timestamp() where id = :id", p);
        }
        jdbc.update("""
                update crm_envoi_masse set statut = 'TERMINE', termine_le = utc_timestamp(), updated_at = utc_timestamp()
                where id = :envoi and statut = 'EN_COURS'
                  and not exists (select 1 from crm_message_sortant s where s.envoi_masse_id = :envoi and s.statut in ('A_ENVOYER', 'ENVOI'))
                """, Map.of("envoi", message.get("envoi_masse_id")));
    }

    /** Numeros colles puis clients de la liste ; chaque destinataire non retenu porte son motif. */
    private List<Destinataire> destinataires(EnvoiMasseDemandeDTO demande, PerimetreDTO perimetre) {
        String prefixe = parametreService.valeur("whatsapp.prefixe_pays", "225");
        List<Destinataire> lus = new ArrayList<>();
        if (demande.getNumeros() != null) {
            for (String ligne : demande.getNumeros().split("\\R")) {
                if (ligne.isBlank()) {
                    continue;
                }
                String[] morceaux = ligne.split("[;\\t]", 2);
                String saisi = morceaux[0].trim();
                String nom = morceaux.length > 1 && !morceaux[1].isBlank() ? morceaux[1].trim() : null;
                Optional<String> numero = ValeursImport.numeroWhatsapp(saisi, prefixe);
                lus.add(new Destinataire(saisi, numero.orElse(null), nom, null, numero.isPresent() ? null : "Format invalide"));
            }
        }
        if (demande.getClientIds() != null && !demande.getClientIds().isEmpty()) {
            Query requete = em.createNativeQuery("""
                    select ct.numero_whatsapp, c.raison_sociale, c.id from crm_contact ct join crm_client c on c.id = ct.client_id
                    where ct.client_id in (:ids) and ct.statut = 'ACTIF' and ct.numero_whatsapp is not null"""
                    + (perimetre.isVisionGlobale() ? "" : " and c.agence_id in (:agences)")
                    + " order by c.raison_sociale, ct.fonction = 'PRINCIPAL' desc, ct.rang, ct.id")
                    .setParameter("ids", demande.getClientIds().stream().distinct().toList());
            if (!perimetre.isVisionGlobale()) {
                requete.setParameter("agences", perimetre.getAgenceIds() == null || perimetre.getAgenceIds().isEmpty() ? List.of(-1L) : perimetre.getAgenceIds());
            }
            @SuppressWarnings("unchecked")
            List<Object[]> contacts = requete.getResultList();
            contacts.forEach(c -> lus.add(new Destinataire((String) c[0], (String) c[0], (String) c[1], ((Number) c[2]).longValue(), null)));
        }
        if (demande.getListeId() != null) {
            ListeDiffusionDTO liste = listeDiffusionService.consulterDefinition(demande.getListeId());
            List<Long> clients = listeDiffusionService.clients(demande.getListeId(), perimetre.isVisionGlobale() ? null : perimetre.getAgenceIds());
            if (!clients.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Object[]> contacts = em.createNativeQuery("""
                        select ct.numero_whatsapp, c.raison_sociale, c.id from crm_contact ct join crm_client c on c.id = ct.client_id
                        where ct.client_id in (:ids) and ct.statut = 'ACTIF' and ct.fonction in (:fonctions) and ct.numero_whatsapp is not null
                        order by c.raison_sociale, ct.fonction = 'PRINCIPAL' desc, ct.rang, ct.id
                        """).setParameter("ids", clients).setParameter("fonctions", liste.getFonctions()).getResultList();
                contacts.forEach(c -> lus.add(new Destinataire((String) c[0], (String) c[0], (String) c[1], ((Number) c[2]).longValue(), null)));
            }
        }
        // Clients des numeros : desabonnes et desactives sont exclus
        List<String> numeros = lus.stream().map(Destinataire::numero).filter(n -> n != null).distinct().toList();
        Map<String, Object[]> clients = new HashMap<>();
        if (!numeros.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Object[]> lignes = em.createNativeQuery("""
                    select ct.numero_whatsapp, c.id, c.raison_sociale, c.statut, c.desabonne_marketing_le from crm_contact ct join crm_client c on c.id = ct.client_id
                    where ct.numero_whatsapp in (:numeros) and ct.statut = 'ACTIF' order by c.statut = 'ACTIF', c.desabonne_marketing_le is null
                    """).setParameter("numeros", numeros).getResultList();
            lignes.forEach(l -> clients.put((String) l[0], l));
        }
        Map<String, Destinataire> uniques = new LinkedHashMap<>();
        List<Destinataire> resultat = new ArrayList<>();
        for (Destinataire d : lus) {
            if (d.numero() == null) {
                resultat.add(d);
                continue;
            }
            if (uniques.containsKey(d.numero())) {
                if (d.clientId() == null) {
                    resultat.add(new Destinataire(d.saisi(), null, d.nom(), null, "En double"));
                }
                continue;
            }
            Object[] client = clients.get(d.numero());
            Long clientId = client == null ? d.clientId() : Long.valueOf(((Number) client[1]).longValue());
            String nom = d.nom() != null ? d.nom() : client == null ? null : (String) client[2];
            String motif = client == null ? null : client[4] != null ? "Désabonné (STOP)" : !"ACTIF".equals(client[3]) ? "Client désactivé" : null;
            Destinataire retenu = new Destinataire(d.saisi(), d.numero(), nom, clientId, motif);
            uniques.put(d.numero(), retenu);
            resultat.add(retenu);
        }
        return resultat;
    }

    /**
     * Sans piece jointe : le texte seul. Une 1re image porte le texte en legende s'il est court ; sinon le texte part
     * d'abord. Les autres fichiers suivent sans legende ; un fichier perdu est signale dans le motif, sans renvoyer le texte.
     */
    private ResultatEnvoiDTO envoyer(String numero, String destinataire, String texte, List<FichierDTO> pieces) {
        List<FichierDTO> reste = new ArrayList<>(pieces);
        ResultatEnvoiDTO principal = !reste.isEmpty() && reste.getFirst().getTypeContenu().startsWith("image/") && texte.length() <= LEGENDE_MAX
                ? passerelle.envoyerFichier(numero, destinataire, reste.removeFirst(), texte)
                : passerelle.envoyerTexte(numero, destinataire, texte);
        if (!principal.isSucces()) {
            return principal;
        }
        for (FichierDTO fichier : reste) {
            ResultatEnvoiDTO piece = passerelle.envoyerFichier(numero, destinataire, fichier, null);
            if (!piece.isSucces()) {
                principal.setErreur(fichier.getNom() + " non envoyé : " + piece.getErreur());
                break;
            }
        }
        return principal;
    }

    private void verifierModele(Long modeleId) {
        if (modeleId == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<String> statuts = em.createNativeQuery("select statut from crm_modele_message where id = :id and module = 'MARKETING' and canal = 'WHATSAPP'")
                .setParameter("id", modeleId).getResultList();
        if (statuts.isEmpty() || !"ACTIF".equals(statuts.getFirst())) {
            throw new ErreurMetierException("Choisissez un modèle WhatsApp actif du Marketing.");
        }
    }

    private static boolean remplissable(String source) {
        return source != null && (source.startsWith("client.") || SOURCES_CALCULEES.contains(source));
    }

    /**
     * Texte de chaque destinataire : {nom} et les variables du client (lues en une requete), de l'entreprise et de
     * l'expediteur. Pour un numero hors base, les variables du nom du client prennent le nom saisi ; une ligne qui cite
     * une variable propre aux campagnes (promotion, information, facture...) disparait.
     */
    private final class Personnalisation {
        private final String contenu;
        private final Map<String, String> sources = new HashMap<>();
        private final Map<String, String> communes = new HashMap<>();
        private final Map<Long, Map<String, String>> parClient = new HashMap<>();
        /** Variables propres aux campagnes : leur ligne est retiree du message. */
        private final List<String> retirees = new ArrayList<>();

        Personnalisation(String texte, List<Destinataire> destinataires, String expediteur) {
            Map<String, String> sourcesParCode = variableMessageService.sourcesParCode();
            Set<String> utilisees = GabaritMessage.variables(texte);
            Set<String> horsEnvoi = new java.util.HashSet<>();
            for (String code : utilisees) {
                String source = sourcesParCode.get(code);
                if (source != null && remplissable(source)) {
                    sources.put(code, source);
                } else if (source != null) {
                    horsEnvoi.add(code);
                }
            }
            this.contenu = GabaritMessage.retirerLignes(texte.strip(), horsEnvoi);
            horsEnvoi.stream().sorted().forEach(retirees::add);
            String auteur = (String) em.createNativeQuery("select nom_complet from crm_utilisateur where id = :id")
                    .setParameter("id", perimetreService.perimetreCourant().getUtilisateurId()).getSingleResult();
            sources.forEach((code, source) -> {
                switch (source) {
                    case "calcul.expediteur_nom" -> communes.put(code, auteur);
                    case "calcul.expediteur_numero" -> communes.put(code, SelectionCampagne.numeroLisible(expediteur));
                    case "calcul.site_entreprise", "calcul.lien_commande", "calcul.telephone_entreprise", "calcul.nom_entreprise" ->
                        communes.put(code, SelectionCampagne.valeurEntreprise(parametreService, source));
                    default -> { }
                }
            });
            List<Long> clients = destinataires.stream().map(Destinataire::clientId).filter(c -> c != null).distinct().toList();
            Map<String, String> colonnes = new LinkedHashMap<>();
            sources.forEach((code, source) -> {
                if (source.startsWith("client.") && COLONNE.matcher(source.substring(7)).matches()) {
                    colonnes.put(code, source.substring(7));
                }
            });
            boolean agence = sources.containsValue("calcul.agence");
            boolean segment = sources.containsValue("calcul.segment");
            if (clients.isEmpty() || colonnes.isEmpty() && !agence && !segment) {
                return;
            }
            StringBuilder select = new StringBuilder("select c.id as id, ag.libelle as agence, sg.libelle as segment");
            List<String> alias = new ArrayList<>();
            colonnes.forEach((code, colonne) -> {
                select.append(", c.`").append(colonne).append("` as v").append(alias.size());
                alias.add(code);
            });
            @SuppressWarnings("unchecked")
            List<Tuple> fiches = em.createNativeQuery(select + """

                    from crm_client c join crm_agence ag on ag.id = c.agence_id left join crm_segment sg on sg.code = c.segmentation
                    where c.id in (:ids)
                    """, Tuple.class).setParameter("ids", clients).getResultList();
            for (Tuple t : fiches) {
                Map<String, String> valeurs = new HashMap<>();
                for (int i = 0; i < alias.size(); i++) {
                    valeurs.put(alias.get(i), SelectionCampagne.formater(t.get("v" + i)));
                }
                sources.forEach((code, source) -> {
                    if ("calcul.agence".equals(source)) {
                        valeurs.put(code, t.get("agence", String.class));
                    } else if ("calcul.segment".equals(source)) {
                        valeurs.put(code, t.get("segment", String.class));
                    }
                });
                parClient.put(t.get("id", Long.class), valeurs);
            }
        }

        String rendre(Destinataire d) {
            Map<String, String> valeurs = new HashMap<>(communes);
            Map<String, String> client = d.clientId() == null ? null : parClient.get(d.clientId());
            sources.forEach((code, source) -> {
                if (client != null && client.containsKey(code)) {
                    valeurs.put(code, client.get(code));
                } else if (!valeurs.containsKey(code)) {
                    valeurs.put(code, "client.raison_sociale".equals(source) && d.nom() != null ? d.nom() : "");
                }
            });
            String texte = sources.isEmpty() ? contenu : GabaritMessage.rendre(contenu, valeurs);
            return personnaliser(texte, d.nom());
        }
    }

    /** {nom} : le nom saisi a cote du numero, ou celui du client. */
    static String personnaliser(String contenu, String nom) {
        return contenu.strip().replace("{nom}", nom == null ? "" : nom).replaceAll("[ \\t]+([,.!?])", "$1").replaceAll(" {2,}", " ");
    }

    EnvoiMasseDTO mapTupleToDto(Tuple tuple) {
        return EnvoiMasseDTO.builder()
                .id(tuple.get("id", Long.class))
                .nom(tuple.get("nom", String.class))
                .numeroExpediteur(tuple.get("numeroExpediteur", String.class))
                .contenu(tuple.get("contenu", String.class))
                .statut(tuple.get("statut", String.class))
                .nbDestinataires(tuple.get("nbDestinataires", Integer.class))
                .nbExclus(tuple.get("nbExclus", Integer.class))
                .nbEnAttente(((Number) tuple.get("nbEnAttente")).longValue())
                .nbEnvoyes(((Number) tuple.get("nbEnvoyes")).longValue())
                .nbDistribues(((Number) tuple.get("nbDistribues")).longValue())
                .nbLus(((Number) tuple.get("nbLus")).longValue())
                .nbEchecs(((Number) tuple.get("nbEchecs")).longValue())
                .creePar(tuple.get("creePar", String.class))
                .creeLe(tuple.get("creeLe", LocalDateTime.class))
                .annulePar(tuple.get("annulePar", String.class))
                .termineLe(tuple.get("termineLe", LocalDateTime.class))
                .modeleId(tuple.get("modeleId", Long.class))
                .modeleNom(tuple.get("modeleNom", String.class))
                .departLe(tuple.get("departLe", LocalDateTime.class))
                .build();
    }
}
