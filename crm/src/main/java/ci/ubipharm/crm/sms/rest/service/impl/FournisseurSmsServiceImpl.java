package ci.ubipharm.crm.sms.rest.service.impl;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.securite.rest.service.JournalActionService;
import ci.ubipharm.crm.securite.rest.service.UtilisateurCourantService;
import ci.ubipharm.crm.sms.rest.service.FournisseurSmsService;
import ci.ubipharm.crm.sms.rest.service.dto.EssaiSmsDTO;
import ci.ubipharm.crm.sms.rest.service.dto.FournisseurSmsDTO;
import ci.ubipharm.crm.sms.rest.service.dto.FournisseurSmsDemandeDTO;
import ci.ubipharm.crm.sms.rest.service.dto.ResultatAppelSmsDTO;
import ci.ubipharm.crm.sms.rest.service.impl.PasserelleSmsHttp.Configuration;
import ci.ubipharm.crm.sms.rest.service.impl.PasserelleSmsHttp.Reponse;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.socle.erreur.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FournisseurSmsServiceImpl implements FournisseurSmsService {

    private static final SecureRandom HASARD = new SecureRandom();

    private static final String SELECT = """
            select f.id as id, f.code as code, f.libelle as libelle, f.actif as actif, f.par_defaut as parDefaut, f.expediteur as expediteur,
                   f.cle_api is not null and f.cle_api <> '' as cleRenseignee, f.format_numero as formatNumero, f.url_envoi as urlEnvoi,
                   f.methode_envoi as methodeEnvoi, f.type_contenu as typeContenu, f.entetes as entetes, f.corps as corps,
                   f.chemin_identifiant as cheminIdentifiant, f.chemin_nb_sms as cheminNbSms, f.chemin_erreur as cheminErreur,
                   f.url_statut as urlStatut, f.chemin_statut as cheminStatut, f.champ_identifiant_accuse as champIdentifiantAccuse,
                   f.champ_statut_accuse as champStatutAccuse, f.correspondance_statuts as correspondanceStatuts, f.url_solde as urlSolde,
                   f.chemin_solde as cheminSolde, u.nom_complet as modifiePar, coalesce(f.updated_at, f.created_at) as modifieLe
            from crm_fournisseur_sms f
            left join crm_utilisateur u on u.id = f.modifie_par
            """;

    private final EntityManager em;
    private final PasserelleSmsHttp passerelle;
    private final JournalActionService journalActionService;
    private final UtilisateurCourantService utilisateurCourantService;

    @Override
    @Transactional(readOnly = true)
    public List<FournisseurSmsDTO> lister() {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " order by f.par_defaut desc, f.actif desc, f.libelle", Tuple.class).getResultList();
        return lignes.stream().map(this::mapTupleToDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FournisseurSmsDTO consulter(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(SELECT + " where f.id = :id", Tuple.class).setParameter("id", id).getResultList();
        if (lignes.isEmpty()) {
            throw new RessourceIntrouvableException("Fournisseur SMS introuvable.");
        }
        return mapTupleToDto(lignes.getFirst());
    }

    @Override
    @Transactional
    public FournisseurSmsDTO creer(FournisseurSmsDemandeDTO demande) {
        String code = demande.getCode().trim().toUpperCase(Locale.ROOT);
        Number existe = (Number) em.createNativeQuery("select count(*) from crm_fournisseur_sms where code = :code")
                .setParameter("code", code).getSingleResult();
        if (existe.longValue() > 0) {
            throw new ErreurMetierException("Un fournisseur porte déjà le code " + code + ".");
        }
        Query insertion = em.createNativeQuery("""
                insert into crm_fournisseur_sms (code, libelle, actif, par_defaut, expediteur, cle_api, format_numero, url_envoi, methode_envoi,
                    type_contenu, entetes, corps, chemin_identifiant, chemin_nb_sms, chemin_erreur, url_statut, chemin_statut,
                    champ_identifiant_accuse, champ_statut_accuse, correspondance_statuts, jeton_accuse, url_solde, chemin_solde,
                    modifie_par, created_at)
                values (:code, :libelle, :actif, false, :expediteur, :cle, :formatNumero, :urlEnvoi, :methodeEnvoi, :typeContenu, :entetes, :corps,
                    :cheminIdentifiant, :cheminNbSms, :cheminErreur, :urlStatut, :cheminStatut, :champIdentifiantAccuse, :champStatutAccuse,
                    :correspondanceStatuts, :jeton, :urlSolde, :cheminSolde, :par, utc_timestamp())
                """)
                .setParameter("code", code)
                .setParameter("cle", texte(demande.getCleApi()))
                .setParameter("jeton", HexFormat.of().formatHex(HASARD.generateSeed(16)));
        valeurs(insertion, demande).executeUpdate();
        Long id = ((Number) em.createNativeQuery("select id from crm_fournisseur_sms where code = :code").setParameter("code", code)
                .getSingleResult()).longValue();
        journalActionService.enregistrer("FOURNISSEUR_SMS_CREATION", "crm_fournisseur_sms", id, code);
        return consulter(id);
    }

    @Override
    @Transactional
    public FournisseurSmsDTO modifier(Long id, FournisseurSmsDemandeDTO demande) {
        FournisseurSmsDTO actuel = consulter(id);
        if (actuel.isParDefaut() && !demande.isActif()) {
            throw new ErreurMetierException("Le fournisseur par défaut reste actif : choisissez d'abord un autre fournisseur par défaut.");
        }
        Query modification = em.createNativeQuery("""
                update crm_fournisseur_sms set libelle = :libelle, actif = :actif, expediteur = :expediteur,
                    cle_api = case when :cle is null then cle_api else :cle end, format_numero = :formatNumero, url_envoi = :urlEnvoi,
                    methode_envoi = :methodeEnvoi, type_contenu = :typeContenu, entetes = :entetes, corps = :corps,
                    chemin_identifiant = :cheminIdentifiant, chemin_nb_sms = :cheminNbSms, chemin_erreur = :cheminErreur, url_statut = :urlStatut,
                    chemin_statut = :cheminStatut, champ_identifiant_accuse = :champIdentifiantAccuse, champ_statut_accuse = :champStatutAccuse,
                    correspondance_statuts = :correspondanceStatuts, url_solde = :urlSolde, chemin_solde = :cheminSolde,
                    modifie_par = :par, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("id", id)
                .setParameter("cle", texte(demande.getCleApi()));
        valeurs(modification, demande).executeUpdate();
        boolean nouvelleCle = texte(demande.getCleApi()) != null;
        journalActionService.enregistrer("FOURNISSEUR_SMS_MODIFICATION", "crm_fournisseur_sms", id,
                actuel.getCode() + (nouvelleCle ? " : clé API remplacée" : ""));
        return consulter(id);
    }

    @Override
    @Transactional
    public FournisseurSmsDTO definirParDefaut(Long id) {
        FournisseurSmsDTO fournisseur = consulter(id);
        if (!fournisseur.isActif()) {
            throw new ErreurMetierException("Activez ce fournisseur avant d'en faire le fournisseur par défaut.");
        }
        em.createNativeQuery("update crm_fournisseur_sms set par_defaut = false, updated_at = utc_timestamp() where par_defaut = true and id <> :id")
                .setParameter("id", id).executeUpdate();
        em.createNativeQuery("update crm_fournisseur_sms set par_defaut = true, modifie_par = :par, updated_at = utc_timestamp() where id = :id")
                .setParameter("id", id)
                .setParameter("par", utilisateurCourantService.utilisateur().getId())
                .executeUpdate();
        journalActionService.enregistrer("FOURNISSEUR_SMS_DEFAUT", "crm_fournisseur_sms", id, fournisseur.getCode());
        return consulter(id);
    }

    @Override
    public ResultatAppelSmsDTO essayer(Long id, EssaiSmsDTO essai) {
        Configuration c = configuration(id);
        String numero = PasserelleSmsHttp.numero(c, essai.getNumero());
        if (!numero.replace("+", "").matches("\\d{8,15}")) {
            throw new ErreurMetierException("Numéro de téléphone invalide.");
        }
        Reponse reponse = passerelle.envoyer(c, essai.getNumero(), essai.getMessage(), "CRM-ESSAI");
        journalActionService.enregistrer("FOURNISSEUR_SMS_ESSAI", "crm_fournisseur_sms", id, numero + " : HTTP " + reponse.statutHttp());
        return ResultatAppelSmsDTO.builder()
                .reussi(reponse.reussie())
                .statutHttp(reponse.injoignable() ? null : reponse.statutHttp())
                .identifiant(reponse.identifiant())
                .nbSms(reponse.nbSms())
                .erreur(reponse.reussie() ? null : reponse.erreur())
                .reponse(reponse.apercu())
                .build();
    }

    @Override
    public ResultatAppelSmsDTO solde(Long id) {
        Configuration c = configuration(id);
        if (c.urlSolde() == null || c.urlSolde().isBlank()) {
            throw new ErreurMetierException("Ce fournisseur n'a pas d'adresse de solde.");
        }
        Reponse reponse = passerelle.solde(c);
        return ResultatAppelSmsDTO.builder()
                .reussi(reponse.reussie())
                .statutHttp(reponse.injoignable() ? null : reponse.statutHttp())
                .solde(reponse.reussie() ? passerelle.lireSolde(c, reponse) : null)
                .erreur(reponse.reussie() ? null : reponse.injoignable() ? reponse.erreur() : "Réponse HTTP " + reponse.statutHttp())
                .reponse(reponse.apercu())
                .build();
    }

    private Configuration configuration(Long id) {
        Configuration c = passerelle.parId(id);
        if (c == null) {
            throw new RessourceIntrouvableException("Fournisseur SMS introuvable.");
        }
        return c;
    }

    private Query valeurs(Query requete, FournisseurSmsDemandeDTO d) {
        return requete
                .setParameter("libelle", d.getLibelle().trim())
                .setParameter("actif", d.isActif())
                .setParameter("expediteur", texte(d.getExpediteur()))
                .setParameter("formatNumero", d.getFormatNumero() == null ? "INTERNATIONAL" : d.getFormatNumero())
                .setParameter("urlEnvoi", d.getUrlEnvoi().trim())
                .setParameter("methodeEnvoi", d.getMethodeEnvoi() == null ? "POST" : d.getMethodeEnvoi())
                .setParameter("typeContenu", texte(d.getTypeContenu()) == null ? "application/json" : d.getTypeContenu().trim())
                .setParameter("entetes", texte(d.getEntetes()))
                .setParameter("corps", texte(d.getCorps()))
                .setParameter("cheminIdentifiant", texte(d.getCheminIdentifiant()))
                .setParameter("cheminNbSms", texte(d.getCheminNbSms()))
                .setParameter("cheminErreur", texte(d.getCheminErreur()))
                .setParameter("urlStatut", texte(d.getUrlStatut()))
                .setParameter("cheminStatut", texte(d.getCheminStatut()))
                .setParameter("champIdentifiantAccuse", texte(d.getChampIdentifiantAccuse()))
                .setParameter("champStatutAccuse", texte(d.getChampStatutAccuse()))
                .setParameter("correspondanceStatuts", texte(d.getCorrespondanceStatuts()))
                .setParameter("urlSolde", texte(d.getUrlSolde()))
                .setParameter("cheminSolde", texte(d.getCheminSolde()))
                .setParameter("par", utilisateurCourantService.utilisateur().getId());
    }

    private static String texte(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static boolean vrai(Object valeur) {
        return valeur instanceof Boolean b ? b : valeur instanceof Number n && n.intValue() != 0;
    }

    FournisseurSmsDTO mapTupleToDto(Tuple tuple) {
        String code = tuple.get("code", String.class);
        Configuration c = passerelle.parCode(code);
        return FournisseurSmsDTO.builder()
                .id(((Number) tuple.get("id")).longValue())
                .code(code)
                .libelle(tuple.get("libelle", String.class))
                .actif(vrai(tuple.get("actif")))
                .parDefaut(vrai(tuple.get("parDefaut")))
                .expediteur(tuple.get("expediteur", String.class))
                .cleRenseignee(vrai(tuple.get("cleRenseignee")))
                .formatNumero(tuple.get("formatNumero", String.class))
                .urlEnvoi(tuple.get("urlEnvoi", String.class))
                .methodeEnvoi(tuple.get("methodeEnvoi", String.class))
                .typeContenu(tuple.get("typeContenu", String.class))
                .entetes(tuple.get("entetes", String.class))
                .corps(tuple.get("corps", String.class))
                .cheminIdentifiant(tuple.get("cheminIdentifiant", String.class))
                .cheminNbSms(tuple.get("cheminNbSms", String.class))
                .cheminErreur(tuple.get("cheminErreur", String.class))
                .urlStatut(tuple.get("urlStatut", String.class))
                .cheminStatut(tuple.get("cheminStatut", String.class))
                .champIdentifiantAccuse(tuple.get("champIdentifiantAccuse", String.class))
                .champStatutAccuse(tuple.get("champStatutAccuse", String.class))
                .correspondanceStatuts(tuple.get("correspondanceStatuts", String.class))
                .urlSolde(tuple.get("urlSolde", String.class))
                .cheminSolde(tuple.get("cheminSolde", String.class))
                .urlAccuse(c == null ? null : passerelle.urlAccuse(c))
                .modifiePar(tuple.get("modifiePar", String.class))
                .modifieLe(tuple.get("modifieLe", LocalDateTime.class))
                .build();
    }
}
