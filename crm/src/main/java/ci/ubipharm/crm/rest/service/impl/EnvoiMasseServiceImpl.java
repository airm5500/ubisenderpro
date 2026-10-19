package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import ci.ubipharm.crm.rest.service.EnvoiMasseService;
import ci.ubipharm.crm.rest.service.JournalActionService;
import ci.ubipharm.crm.rest.service.ListeDiffusionService;
import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.dto.DestinataireMasseDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiMasseApercuDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiMasseDTO;
import ci.ubipharm.crm.rest.service.dto.EnvoiMasseDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.ListeDiffusionDTO;
import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.rest.service.dto.ResultatEnvoiDTO;
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
                   a.nom_complet as annulePar, e.termine_le as termineLe,
                   (select count(*) from crm_message_sortant s where s.envoi_masse_id = e.id and s.statut in ('A_ENVOYER', 'ENVOI')) as nbEnAttente,
                   (select count(*) from crm_message_sortant s where s.envoi_masse_id = e.id and s.statut in ('ENVOYE', 'DISTRIBUE', 'LU')) as nbEnvoyes,
                   (select count(*) from crm_message_sortant s where s.envoi_masse_id = e.id and s.statut in ('DISTRIBUE', 'LU')) as nbDistribues,
                   (select count(*) from crm_message_sortant s where s.envoi_masse_id = e.id and s.statut = 'LU') as nbLus,
                   (select count(*) from crm_message_sortant s where s.envoi_masse_id = e.id and s.statut = 'ECHEC') as nbEchecs
            from crm_envoi_masse e join crm_utilisateur c on c.id = e.cree_par left join crm_utilisateur a on a.id = e.annule_par
            """;

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
        if ((demande.getNumeros() == null || demande.getNumeros().isBlank()) && demande.getListeId() == null) {
            throw new ErreurMetierException("Collez des numéros ou choisissez une liste de diffusion.");
        }
        List<Destinataire> destinataires = destinataires(demande, perimetre);
        int max = parametreService.entier("whatsapp.envoi_masse.destinataires_max", 1000);
        List<Destinataire> retenus = destinataires.stream().filter(d -> d.motif() == null).toList();
        if (retenus.size() > max) {
            throw new ErreurMetierException(retenus.size() + " destinataires : un envoi en masse en compte au plus " + max + " (Paramètres).");
        }
        List<DestinataireMasseDTO> exclus = destinataires.stream().filter(d -> d.motif() != null)
                .map(d -> DestinataireMasseDTO.builder().saisi(d.saisi()).numero(d.numero()).nom(d.nom()).clientId(d.clientId()).statut("EXCLU").motif(d.motif()).build())
                .toList();
        String exemple = retenus.isEmpty() ? null : personnaliser(demande.getContenu(), retenus.getFirst().nom());
        if (demande.isApercu()) {
            return EnvoiMasseApercuDTO.builder().nbRetenus(retenus.size()).exclus(exclus).exemple(exemple)
                    .horsPlage(!cadence.dansLaPlage()).plageHoraire(cadence.plage()).build();
        }
        if (retenus.isEmpty()) {
            throw new ErreurMetierException("Aucun destinataire à qui envoyer : tous sont exclus.");
        }
        em.createNativeQuery("""
                insert into crm_envoi_masse (nom, numero_expediteur, contenu, statut, nb_destinataires, nb_exclus, cree_par, created_at)
                values (:nom, :expediteur, :contenu, 'EN_COURS', :nb, :exclus, :par, utc_timestamp())
                """).setParameter("nom", demande.getNom().trim()).setParameter("expediteur", expediteur).setParameter("contenu", demande.getContenu().strip())
                .setParameter("nb", retenus.size()).setParameter("exclus", exclus.size()).setParameter("par", perimetre.getUtilisateurId()).executeUpdate();
        Long id = ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
        for (Destinataire d : destinataires) {
            if (d.numero() == null) {
                continue;
            }
            em.createNativeQuery("""
                    insert ignore into crm_message_sortant (origine, envoi_masse_id, numero_expediteur, numero_destinataire, nom_destinataire, client_id,
                                                            contenu, statut, motif, cree_par, created_at)
                    values ('ENVOI_MASSE', :envoi, :expediteur, :numero, :nom, :client, :contenu, :statut, :motif, :par, utc_timestamp())
                    """).setParameter("envoi", id).setParameter("expediteur", expediteur).setParameter("numero", d.numero()).setParameter("nom", d.nom())
                    .setParameter("client", d.clientId()).setParameter("contenu", personnaliser(demande.getContenu(), d.nom()))
                    .setParameter("statut", d.motif() == null ? "A_ENVOYER" : "EXCLU").setParameter("motif", d.motif())
                    .setParameter("par", perimetre.getUtilisateurId()).executeUpdate();
        }
        journalActionService.enregistrer("ENVOI_MASSE_CREATION", "crm_envoi_masse", id,
                demande.getNom().trim() + " : " + retenus.size() + " destinataire(s), " + exclus.size() + " exclu(s), depuis " + expediteur);
        return EnvoiMasseApercuDTO.builder().envoi(consulter(id)).nbRetenus(retenus.size()).exclus(exclus).exemple(exemple)
                .horsPlage(!cadence.dansLaPlage()).plageHoraire(cadence.plage()).build();
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
        envoi.setHorsPlage(!cadence.dansLaPlage());
        envoi.setPlageHoraire(cadence.plage());
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
        if (!cadence.dansLaPlage()) {
            return 0;
        }
        // Les relances et promotions d'un numero passent avant ses envois en masse
        List<String> numeros = jdbc.queryForList("""
                select distinct s.numero_expediteur from crm_message_sortant s
                join crm_envoi_masse e on e.id = s.envoi_masse_id and e.statut = 'EN_COURS'
                join crm_session_whatsapp w on w.numero = s.numero_expediteur and w.statut = 'CONNECTE' and w.fournisseur = 'WA_WEB'
                where s.statut = 'A_ENVOYER' and not exists (
                    select 1 from crm_campagne_message m join crm_campagne c on c.id = m.campagne_id
                    where m.numero_expediteur = s.numero_expediteur and m.statut = 'A_ENVOYER' and c.statut = 'EN_COURS')
                """, Map.of(), String.class);
        int envoyes = 0;
        for (String numero : numeros) {
            if (!cadence.peutEnvoyer(numero)) {
                continue;
            }
            Map<String, Object> message = reserver(numero);
            if (message == null) {
                continue;
            }
            ResultatEnvoiDTO resultat = passerelle.envoyerTexte(numero, (String) message.get("numero_destinataire"), (String) message.get("contenu"));
            transaction.executeWithoutResult(statut -> enregistrer(message, resultat));
            cadence.envoye(numero);
            envoyes++;
        }
        return envoyes;
    }

    @Override
    public int reprendreEnvoisInterrompus() {
        return transaction.execute(statut -> jdbc.update("""
                update crm_message_sortant set statut = 'ECHEC', updated_at = utc_timestamp(),
                       motif = 'Envoi interrompu : verifiez dans le WhatsApp de l''expediteur avant de renvoyer'
                where statut = 'ENVOI'
                """, Map.of()));
    }

    private Map<String, Object> reserver(String numero) {
        return transaction.execute(statut -> {
            List<Map<String, Object>> messages = jdbc.queryForList("""
                    select s.id, s.envoi_masse_id, s.numero_destinataire, s.contenu, s.tentatives
                    from crm_message_sortant s join crm_envoi_masse e on e.id = s.envoi_masse_id
                    where s.numero_expediteur = :numero and s.statut = 'A_ENVOYER' and e.statut = 'EN_COURS'
                    order by e.created_at, s.id limit 1 for update
                    """, Map.of("numero", numero));
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
                .build();
    }
}
