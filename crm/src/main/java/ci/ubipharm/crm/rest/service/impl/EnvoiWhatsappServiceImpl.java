package ci.ubipharm.crm.rest.service.impl;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import ci.ubipharm.crm.rest.service.EnvoiWhatsappService;
import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.PasserelleMetaService;
import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.rest.service.PieceJointeService;
import ci.ubipharm.crm.rest.service.SuiviEnvoiService;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.ResultatEnvoiDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnvoiWhatsappServiceImpl implements EnvoiWhatsappService {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");
    /** WhatsApp coupe les legendes d'image trop longues : au-dela, le texte part a part. */
    private static final int LEGENDE_MAX = 1000;
    private static final String CLASSEUR = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PasserelleWhatsappService passerelle;
    private final PasserelleMetaService passerelleMeta;
    private final ParametreService parametreService;
    private final SuiviEnvoiService suiviEnvoi;
    private final PieceJointeService pieceJointeService;

    /** Prochain envoi autorise par numero d'agent. */
    private final Map<String, Instant> prochainEnvoi = new ConcurrentHashMap<>();

    private record Message(Long id, Long campagneId, Long factureId, String numeroExpediteur, String numeroDestinataire, String contenu,
            int tentatives, String parametres, String modeleNom, String metaModele, String metaLangue, String metaEntete) {
    }

    /** Envoi libre (texte, image, document) par WhatsApp Web ou, dans la fenetre de 24 h, par l'API officielle. */
    private interface Canal {
        ResultatEnvoiDTO texte(String destinataire, String texte);

        ResultatEnvoiDTO fichier(String destinataire, FichierDTO fichier, String legende);
    }

    @Override
    public int envoyerLot() {
        int heure = ZonedDateTime.now(ABIDJAN).getHour();
        if (heure < parametreService.entier("whatsapp.envoi.heure_debut", 8) || heure >= parametreService.entier("whatsapp.envoi.heure_fin", 18)) {
            return 0;
        }
        int plafond = parametreService.entier("whatsapp.envoi.plafond_jour", 200);
        List<String> numeros = jdbc.queryForList("""
                select distinct m.numero_expediteur from crm_campagne_message m
                join crm_campagne c on c.id = m.campagne_id
                join crm_session_whatsapp s on s.numero = m.numero_expediteur and s.statut = 'CONNECTE'
                where m.statut = 'A_ENVOYER' and c.statut = 'EN_COURS'
                """, Map.of(), String.class);
        int envoyes = 0;
        for (String numero : numeros) {
            Instant prochain = prochainEnvoi.get(numero);
            if (prochain != null && prochain.isAfter(Instant.now())) {
                continue;
            }
            Long aujourdhui = jdbc.queryForObject("""
                    select count(*) from crm_campagne_message where numero_expediteur = :numero and envoye_le >= utc_date()
                    """, Map.of("numero", numero), Long.class);
            if (aujourdhui != null && aujourdhui >= plafond) {
                continue;
            }
            Message message = reserver(numero);
            if (message == null) {
                continue;
            }
            ResultatEnvoiDTO resultat = envoyer(numero, phoneNumberIdMeta(numero), message);
            transaction.executeWithoutResult(statut -> enregistrer(message, resultat));
            prochainEnvoi.put(numero, Instant.now().plusSeconds(attente()));
            envoyes++;
        }
        return envoyes;
    }

    /**
     * Sans piece jointe : le texte seul. Avec une affiche en image et un texte court : l'image legendee du texte ; sinon
     * l'affiche puis le texte. La liste des produits suit en document. Le message compte comme envoye des que son texte
     * est parti ; une piece jointe perdue ensuite est signalee dans son motif, sans renvoyer le texte.
     */
    private ResultatEnvoiDTO envoyer(String numero, String phoneNumberId, Message message) {
        List<FichierDTO> reste = new ArrayList<>(pieceJointeService.piecesDeCampagne(message.campagneId()));
        if (phoneNumberId == null) {
            return envoyerLibre(new Canal() {
                @Override
                public ResultatEnvoiDTO texte(String destinataire, String texte) {
                    return passerelle.envoyerTexte(numero, destinataire, texte);
                }

                @Override
                public ResultatEnvoiDTO fichier(String destinataire, FichierDTO fichier, String legende) {
                    return passerelle.envoyerFichier(numero, destinataire, fichier, legende);
                }
            }, message, reste);
        }
        if (fenetreOuverte(numero, message.numeroDestinataire())) {
            return envoyerLibre(new Canal() {
                @Override
                public ResultatEnvoiDTO texte(String destinataire, String texte) {
                    return passerelleMeta.envoyerTexte(phoneNumberId, destinataire, texte);
                }

                @Override
                public ResultatEnvoiDTO fichier(String destinataire, FichierDTO fichier, String legende) {
                    return passerelleMeta.envoyerFichier(phoneNumberId, destinataire, fichier, legende);
                }
            }, message, reste);
        }
        return envoyerModeleMeta(phoneNumberId, message, reste);
    }

    /**
     * Hors de la fenetre de 24 h, Meta n'accepte qu'un modele approuve : ses parametres sont les variables du message, son
     * en-tete la premiere piece jointe du bon type. Les autres pieces jointes ne peuvent pas partir.
     */
    private ResultatEnvoiDTO envoyerModeleMeta(String phoneNumberId, Message message, List<FichierDTO> reste) {
        if (message.metaModele() == null) {
            return ResultatEnvoiDTO.builder().erreur("Hors des 24 h qui suivent une réponse du client, Meta n'accepte qu'un modèle approuvé : "
                    + "renseignez le modèle Meta de « " + message.modeleNom() + " »").build();
        }
        FichierDTO entete = null;
        if (!"AUCUN".equals(message.metaEntete())) {
            boolean image = "IMAGE".equals(message.metaEntete());
            entete = reste.stream().filter(f -> f.getTypeContenu().startsWith("image/") == image).findFirst().orElse(null);
            if (entete == null) {
                return ResultatEnvoiDTO.builder().erreur("Le modèle Meta « " + message.metaModele() + " » attend "
                        + (image ? "une image" : "un document") + " en en-tête : joignez-la à la campagne").build();
            }
            reste.remove(entete);
        }
        List<String> parametres = message.parametres() == null || message.parametres().isEmpty() ? List.of()
                : Arrays.asList(message.parametres().split("\n"));
        ResultatEnvoiDTO resultat = passerelleMeta.envoyerModele(phoneNumberId, message.numeroDestinataire(), message.metaModele(),
                message.metaLangue() == null ? "fr" : message.metaLangue(), parametres, entete);
        if (resultat.isSucces() && !reste.isEmpty()) {
            resultat.setErreur(String.join(", ", reste.stream().map(FichierDTO::getNom).toList())
                    + " non envoyé : hors des 24 h, Meta n'accepte que le modèle");
        }
        return resultat;
    }

    /** Le client a ecrit a ce numero il y a moins de 24 h (marge d'une heure) : Meta accepte un message libre. */
    private boolean fenetreOuverte(String numero, String destinataire) {
        Long recus = jdbc.queryForObject("""
                select count(*) from crm_message_recu
                where numero_agent = :numero and numero_client = :client and recu_le >= utc_timestamp() - interval 23 hour
                """, Map.of("numero", numero, "client", destinataire), Long.class);
        return recus != null && recus > 0;
    }

    private String phoneNumberIdMeta(String numero) {
        List<String> ids = jdbc.queryForList("select meta_phone_number_id from crm_session_whatsapp where numero = :numero and fournisseur = 'META'",
                Map.of("numero", numero), String.class);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private ResultatEnvoiDTO envoyerLibre(Canal canal, Message message, List<FichierDTO> reste) {
        String destinataire = message.numeroDestinataire();
        ResultatEnvoiDTO principal;
        if (!reste.isEmpty() && reste.getFirst().getTypeContenu().startsWith("image/") && message.contenu().length() <= LEGENDE_MAX) {
            principal = canal.fichier(destinataire, reste.removeFirst(), message.contenu());
        } else {
            if (!reste.isEmpty() && !CLASSEUR.equals(reste.getFirst().getTypeContenu())) {
                ResultatEnvoiDTO affiche = canal.fichier(destinataire, reste.removeFirst(), null);
                if (!affiche.isSucces()) {
                    return affiche;
                }
            }
            principal = canal.texte(destinataire, message.contenu());
        }
        if (!principal.isSucces()) {
            return principal;
        }
        for (FichierDTO fichier : reste) {
            ResultatEnvoiDTO piece = canal.fichier(destinataire, fichier, null);
            if (!piece.isSucces()) {
                principal.setErreur(fichier.getNom() + " non envoyé : " + piece.getErreur());
                break;
            }
        }
        return principal;
    }

    @Override
    public int reprendreEnvoisInterrompus() {
        return transaction.execute(statut -> {
            List<Long> campagnes = jdbc.queryForList("select distinct campagne_id from crm_campagne_message where statut = 'ENVOI' and canal = 'WHATSAPP'", Map.of(), Long.class);
            int interrompus = jdbc.update("""
                    update crm_campagne_message set statut = 'ECHEC', updated_at = utc_timestamp(),
                           motif = 'Envoi interrompu : verifiez dans le WhatsApp de l''agent avant de relancer'
                    where statut = 'ENVOI' and canal = 'WHATSAPP'
                    """, Map.of());
            campagnes.forEach(suiviEnvoi::recompter);
            return interrompus;
        });
    }

    /** Passe le plus ancien message en attente du numero a ENVOI ; null si un autre passage l'a pris. */
    private Message reserver(String numero) {
        return transaction.execute(statut -> {
            List<Message> messages = jdbc.query("""
                    select m.id, m.campagne_id, m.facture_id, m.numero_expediteur, m.numero_destinataire, m.contenu, m.tentatives,
                           m.parametres, mo.nom, mo.meta_modele, mo.meta_langue, mo.meta_entete
                    from crm_campagne_message m join crm_campagne c on c.id = m.campagne_id
                    join crm_modele_message mo on mo.id = c.modele_id
                    where m.numero_expediteur = :numero and m.statut = 'A_ENVOYER' and c.statut = 'EN_COURS'
                    order by c.lance_le, m.id limit 1 for update
                    """, Map.of("numero", numero), (rs, i) -> new Message(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                    rs.getString(5), rs.getString(6), rs.getInt(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11),
                    rs.getString(12)));
            if (messages.isEmpty()) {
                return null;
            }
            jdbc.update("update crm_campagne_message set statut = 'ENVOI', updated_at = utc_timestamp() where id = :id",
                    Map.of("id", messages.getFirst().id()));
            return messages.getFirst();
        });
    }

    private void enregistrer(Message message, ResultatEnvoiDTO resultat) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", message.id())
                .addValue("identifiant", resultat.getIdentifiant())
                .addValue("motif", resultat.getErreur());
        if (resultat.isSucces()) {
            jdbc.update("""
                    update crm_campagne_message set statut = 'ENVOYE', identifiant_whatsapp = :identifiant, envoye_le = utc_timestamp(),
                           tentatives = tentatives + 1, motif = :motif, updated_at = utc_timestamp()
                    where id = :id
                    """, p);
            suiviEnvoi.factureRelancee(message.factureId());
        } else if (resultat.isSessionIndisponible()) {
            // Le message attend la reconnexion du telephone de l'agent, sans compter d'essai
            jdbc.update("update crm_campagne_message set statut = 'A_ENVOYER', updated_at = utc_timestamp() where id = :id", p);
            jdbc.update("update crm_session_whatsapp set statut = 'DECONNECTE', updated_at = utc_timestamp() where numero = :numero",
                    Map.of("numero", message.numeroExpediteur()));
        } else if (resultat.isErreurTechnique() && message.tentatives() + 1 < parametreService.entier("whatsapp.envoi.tentatives_max", 3)) {
            jdbc.update("update crm_campagne_message set statut = 'A_ENVOYER', tentatives = tentatives + 1, motif = :motif, "
                    + "updated_at = utc_timestamp() where id = :id", p);
        } else {
            jdbc.update("update crm_campagne_message set statut = 'ECHEC', tentatives = tentatives + 1, motif = :motif, "
                    + "updated_at = utc_timestamp() where id = :id", p);
            suiviEnvoi.factureNonRelancee(message.factureId());
        }
        suiviEnvoi.recompter(message.campagneId());
    }

    private long attente() {
        int min = parametreService.entier("whatsapp.envoi.delai_min_secondes", 20);
        int max = Math.max(min, parametreService.entier("whatsapp.envoi.delai_max_secondes", 45));
        return min == max ? min : ThreadLocalRandom.current().nextLong(min, max + 1L);
    }
}
