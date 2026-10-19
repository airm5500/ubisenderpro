package ci.ubipharm.crm.rest.service.impl;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.VariableMessageService;
import ci.ubipharm.crm.rest.service.dto.CriteresCampagneDTO;
import ci.ubipharm.crm.rest.service.dto.MessageCampagneDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

/**
 * Selection des factures d'une campagne et generation de leurs messages : une facture = un message (4.7),
 * envoye au numero WhatsApp principal du client depuis le numero de son agent, a l'adresse e-mail du client
 * avec l'adresse de l'agent en reponse, ou par SMS au meme numero principal sous le nom d'expediteur du fournisseur.
 */
@Component
@RequiredArgsConstructor
class SelectionCampagne {

    static final String EXCLU = "EXCLU";
    static final String A_ENVOYER = "A_ENVOYER";

    private static final DateTimeFormatter DATE_LISIBLE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern COLONNE = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s;,]+@[^@\\s;,]+\\.[^@\\s;,]+");

    private final EntityManager em;
    private final VariableMessageService variableMessageService;
    private final ParametreService parametreService;

    /** Perimetre fige du createur : null = sans restriction. */
    record Perimetre(List<Long> agences, List<Long> agents) {
    }

    /** Message genere et le contact destinataire, garde hors du DTO. */
    record Ligne(MessageCampagneDTO message, Long contactId) {

        boolean envoyable() {
            return A_ENVOYER.equals(message.getStatut());
        }
    }

    /** Modele de la campagne : canal, objet (e-mail) et texte. */
    record Modele(String canal, String objet, String contenu) {
    }

    /** Refuse des criteres incoherents avant tout enregistrement. */
    void verifier(String typeCampagne, CriteresCampagneDTO c) {
        if (c == null) {
            return;
        }
        if (c.getJoursAvantMin() != null && c.getJoursAvantMin() < 1 || c.getJoursAvantMax() != null && c.getJoursAvantMax() < 1) {
            throw new ErreurMetierException("Une relance avant échéance part au plus tard la veille (J-1).");
        }
        if (c.getJoursAvantMin() != null && c.getJoursAvantMax() != null && c.getJoursAvantMin() > c.getJoursAvantMax()) {
            throw new ErreurMetierException("Le premier jour de relance avant échéance doit précéder le dernier.");
        }
        if (c.getJoursRetardMin() != null && c.getJoursRetardMin() < 1) {
            throw new ErreurMetierException("Une relance après échéance commence au premier jour de retard.");
        }
        if (c.getJoursRetardMin() != null && c.getJoursRetardMax() != null && c.getJoursRetardMin() > c.getJoursRetardMax()) {
            throw new ErreurMetierException("Le retard minimum dépasse le retard maximum.");
        }
        if (c.getSoldeMin() != null && c.getSoldeMax() != null && c.getSoldeMin().compareTo(c.getSoldeMax()) > 0) {
            throw new ErreurMetierException("Le solde minimum dépasse le solde maximum.");
        }
        if (c.getEcheanceDu() != null && c.getEcheanceAu() != null && c.getEcheanceDu().isAfter(c.getEcheanceAu())) {
            throw new ErreurMetierException("La période d'échéance est inversée.");
        }
    }

    List<Ligne> selectionner(Long campagneId, String typeCampagne, CriteresCampagneDTO criteres, Perimetre perimetre,
            LocalDate reference, Modele modele) {
        CriteresCampagneDTO c = criteres == null ? new CriteresCampagneDTO() : criteres;
        Map<String, String> sourcesParCode = variableMessageService.sourcesParCode();
        Set<String> utilisees = new LinkedHashSet<>(GabaritMessage.variables(modele.contenu()));
        if (modele.objet() != null) {
            utilisees.addAll(GabaritMessage.variables(modele.objet()));
        }
        Map<String, String> aliasParSource = new LinkedHashMap<>();
        StringBuilder colonnes = new StringBuilder();
        for (String code : utilisees) {
            String source = sourcesParCode.get(code);
            if (source == null || source.startsWith("calcul.") || aliasParSource.containsKey(source)) {
                continue;
            }
            String[] morceaux = source.split("\\.", 2);
            if (morceaux.length != 2 || !COLONNE.matcher(morceaux[1]).matches()) {
                continue;
            }
            String alias = "v" + aliasParSource.size();
            aliasParSource.put(source, alias);
            colonnes.append(", ").append("client".equals(morceaux[0]) ? "c" : "f").append(".`").append(morceaux[1]).append("` as ").append(alias);
        }

        StringBuilder sql = new StringBuilder("""
                select f.id as factureId, f.reference as factureReference, f.date_echeance as dateEcheance, f.solde as solde,
                       datediff(:reference, f.date_echeance) as ecart, c.id as clientId, c.code as clientCode,
                       c.raison_sociale as clientRaisonSociale, c.agence_id as agenceId, ag.libelle as agence, sg.libelle as segment, st.libelle as sousType,
                       u.id as agentId, u.nom_complet as agentNom, u.numero_whatsapp as agentNumero, u.statut as agentStatut,
                       u.email as agentEmail, c.email as clientEmail,
                       ct.id as contactId, ct.numero_whatsapp as numeroDestinataire, f.suivi_date as datePromesse,
                       case when f.suivi in ('LITIGE', 'CONTENTIEUX') then f.suivi
                            when f.suivi = 'PROMESSE' and f.suivi_date >= :reference then 'PROMESSE' end as suspension,
                       (select max(dm2.envoye_le) from crm_campagne_message dm2
                        where dm2.facture_id = f.id and dm2.statut in ('ENVOYE', 'DISTRIBUE', 'LU')) as derniereRelance,
                       coalesce(""" + regle("intervalle_jours") + """
                , :defIntervalle) as intervalle,
                       (select cp.nom from crm_campagne_message m join crm_campagne cp on cp.id = m.campagne_id
                        where m.facture_id = f.id and m.statut = 'A_ENVOYER' and cp.id <> :campagneId limit 1) as campagneEnAttente
                """).append(colonnes).append("""

                from crm_facture f
                join crm_client c on c.id = f.client_id
                join crm_agence ag on ag.id = c.agence_id
                join crm_sous_type_compte st on st.id = f.sous_type_id
                left join crm_segment sg on sg.code = c.segmentation
                left join crm_affectation af on af.client_id = c.id and af.date_fin is null
                left join crm_utilisateur u on u.id = af.agent_id
                left join crm_contact ct on ct.id = (select x.id from crm_contact x where x.client_id = c.id and x.statut = 'ACTIF'
                                                     order by x.fonction = 'PRINCIPAL' desc, x.rang, x.id limit 1)
                where f.solde > 0 and f.nature = 'FACTURE' and f.statut <> 'SOLDEE' and c.statut = 'ACTIF'
                """);
        Map<String, Object> parametres = new HashMap<>();
        parametres.put("reference", reference);
        parametres.put("campagneId", campagneId == null ? 0L : campagneId);
        parametres.put("defIntervalle", parametreService.entier(DelaiRelanceServiceImpl.INTERVALLE, 0));
        if (perimetre.agents() != null) {
            sql.append(" and af.agent_id in (:pAgents)");
            parametres.put("pAgents", perimetre.agents());
        }
        if (perimetre.agences() != null) {
            sql.append(" and c.agence_id in (:pAgences)");
            parametres.put("pAgences", perimetre.agences());
        }
        filtre(sql, parametres, "c.agence_id in (:agenceIds)", "agenceIds", c.getAgenceIds());
        filtre(sql, parametres, "af.agent_id in (:agentIds)", "agentIds", c.getAgentIds());
        filtre(sql, parametres, "c.id in (:clientIds)", "clientIds", c.getClientIds());
        filtre(sql, parametres, "f.sous_type_id in (:sousTypeIds)", "sousTypeIds", c.getSousTypeIds());
        filtre(sql, parametres, "c.segmentation in (:segments)", "segments", c.getSegments());
        filtre(sql, parametres, "f.solde >= :soldeMin", "soldeMin", c.getSoldeMin());
        filtre(sql, parametres, "f.solde <= :soldeMax", "soldeMax", c.getSoldeMax());
        filtre(sql, parametres, "f.date_echeance >= :echeanceDu", "echeanceDu", c.getEcheanceDu());
        filtre(sql, parametres, "f.date_echeance <= :echeanceAu", "echeanceAu", c.getEcheanceAu());

        // Fenetre du type de relance, comptee depuis la date d'execution (ecart > 0 = jours de retard). Sans fenetre propre a la
        // campagne, chaque facture suit les delais de l'agence et du segment de son client, sinon les delais par defaut.
        switch (typeCampagne) {
            case "AVANT_ECHEANCE" -> {
                if (c.getJoursAvantMin() == null) {
                    sql.append(" and datediff(:reference, f.date_echeance) <= -coalesce(").append(regle("avant_jours_min")).append(", :defAvantMin)");
                    parametres.put("defAvantMin", parametreService.entier(DelaiRelanceServiceImpl.AVANT_MIN, 1));
                }
                if (c.getJoursAvantMax() == null) {
                    sql.append(" and datediff(:reference, f.date_echeance) >= -coalesce(").append(regle("avant_jours_max")).append(", :defAvantMax)");
                    parametres.put("defAvantMax", parametreService.entier(DelaiRelanceServiceImpl.AVANT_MAX, 5));
                }
            }
            case "JOUR_ECHEANCE" -> sql.append(" and f.date_echeance = :reference");
            case "APRES_ECHEANCE" -> {
                if (c.getJoursRetardMin() == null) {
                    sql.append(" and datediff(:reference, f.date_echeance) >= coalesce(").append(regle("retard_jours_min")).append(", :defRetardMin)");
                    parametres.put("defRetardMin", parametreService.entier(DelaiRelanceServiceImpl.RETARD_MIN, 1));
                }
            }
            default -> {
            }
        }
        filtre(sql, parametres, "datediff(:reference, f.date_echeance) <= -:avantMin", "avantMin", c.getJoursAvantMin());
        filtre(sql, parametres, "datediff(:reference, f.date_echeance) >= -:avantMax", "avantMax", c.getJoursAvantMax());
        filtre(sql, parametres, "datediff(:reference, f.date_echeance) >= :retardMin", "retardMin", c.getJoursRetardMin());
        filtre(sql, parametres, "datediff(:reference, f.date_echeance) <= :retardMax", "retardMax", c.getJoursRetardMax());
        // Campagne automatique : une facture deja relancee avant ou le jour de l'echeance ne l'est plus ; apres l'echeance,
        // elle l'est a chaque envoi, sauf si le message de l'envoi precedent n'est pas encore parti.
        sql.append(" and not exists (select 1 from crm_campagne_message dm where dm.campagne_id = :campagneId and dm.facture_id = f.id"
                + " and dm.statut in (:dejaTraites))");
        parametres.put("dejaTraites", "AVANT_ECHEANCE".equals(typeCampagne) || "JOUR_ECHEANCE".equals(typeCampagne)
                ? List.of(A_ENVOYER, "ENVOI", "ENVOYE", "DISTRIBUE", "LU") : List.of(A_ENVOYER, "ENVOI"));
        sql.append(" order by u.nom_complet, c.code, f.date_echeance, f.reference");

        Query requete = em.createNativeQuery(sql.toString(), Tuple.class);
        parametres.forEach(requete::setParameter);
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = requete.getResultList();
        List<Ligne> resultat = new ArrayList<>(lignes.size());
        for (Tuple t : lignes) {
            resultat.add(ligne(t, modele, sourcesParCode, aliasParSource, reference));
        }
        return resultat;
    }

    /** Valeur de la regle de delais la plus precise pour le client : agence et segment, agence, segment, puis aucune (null). */
    private static String regle(String colonne) {
        return "(select d." + colonne + " from crm_delai_relance d where d.statut = 'ACTIF' and d." + colonne + " is not null"
                + " and (d.agence_id is null or d.agence_id = c.agence_id) and (d.segment is null or d.segment = c.segmentation)"
                + " order by d.agence_id is null, d.segment is null limit 1)";
    }

    private Ligne ligne(Tuple t, Modele modele, Map<String, String> sourcesParCode, Map<String, String> aliasParSource, LocalDate reference) {
        boolean email = "EMAIL".equals(modele.canal());
        boolean sms = "SMS".equals(modele.canal());
        String adresse = email ? premiereAdresse(t.get("clientEmail", String.class)) : null;
        String motif = motifSuspension(t);
        if (motif == null) {
            motif = motifIntervalle(t, reference);
        }
        if (motif == null) {
            motif = email ? motifExclusionEmail(t, adresse) : sms ? motifExclusionSms(t) : motifExclusion(t);
        }
        String texte = null;
        String objet = null;
        String parametres = null;
        if (motif == null) {
            Map<String, String> valeurs = new HashMap<>();
            sourcesParCode.forEach((code, source) -> valeurs.put(code, valeur(t, source, aliasParSource)));
            texte = GabaritMessage.rendre(modele.contenu(), valeurs);
            objet = email ? GabaritMessage.rendre(modele.objet(), valeurs) : null;
            parametres = email ? null : GabaritMessage.parametres(modele.contenu(), valeurs);
        }
        MessageCampagneDTO message = MessageCampagneDTO.builder()
                .factureId(t.get("factureId", Long.class))
                .factureReference(t.get("factureReference", String.class))
                .dateEcheance(t.get("dateEcheance", LocalDate.class))
                .solde(t.get("solde", BigDecimal.class))
                .clientId(t.get("clientId", Long.class))
                .clientCode(t.get("clientCode", String.class))
                .clientRaisonSociale(t.get("clientRaisonSociale", String.class))
                .agenceId(t.get("agenceId", Long.class))
                .canal(modele.canal())
                .numeroDestinataire(email ? null : t.get("numeroDestinataire", String.class))
                .emailDestinataire(adresse)
                .agentId(t.get("agentId", Long.class))
                .agentNom(t.get("agentNom", String.class))
                .numeroExpediteur(email || sms ? null : t.get("agentNumero", String.class))
                .emailReponse(email ? t.get("agentEmail", String.class) : null)
                .objet(objet)
                .contenu(texte)
                .parametres(parametres)
                .statut(motif == null ? A_ENVOYER : EXCLU)
                .motif(motif)
                .build();
        return new Ligne(message, motif == null && !email ? t.get("contactId", Long.class) : null);
    }

    /** Promesse de paiement en cours, litige ou contentieux : la facture n'est pas relancee. */
    private static String motifSuspension(Tuple t) {
        String suspension = t.get("suspension", String.class);
        if (suspension == null) {
            return null;
        }
        return switch (suspension) {
            case "PROMESSE" -> "Promesse de paiement jusqu'au " + t.get("datePromesse", LocalDate.class).format(DATE_LISIBLE);
            case "LITIGE" -> "Facture en litige";
            default -> "Facture au contentieux";
        };
    }

    /** Delai minimal entre deux relances d'une meme facture, toutes campagnes confondues. */
    private static String motifIntervalle(Tuple t, LocalDate reference) {
        LocalDateTime derniere = t.get("derniereRelance", LocalDateTime.class);
        int intervalle = ((Number) t.get("intervalle")).intValue();
        if (derniere == null || intervalle <= 0 || !reference.isBefore(derniere.toLocalDate().plusDays(intervalle))) {
            return null;
        }
        return "Déjà relancée le " + derniere.toLocalDate().format(DATE_LISIBLE) + " (" + intervalle + " jours minimum entre deux relances)";
    }

    private String motifExclusion(Tuple t) {
        if (t.get("agentId") == null) {
            return "Client sans agent de recouvrement";
        }
        if (!"ACTIF".equals(t.get("agentStatut", String.class))) {
            return "Agent " + t.get("agentNom", String.class) + " désactivé";
        }
        if (t.get("agentNumero") == null) {
            return "Agent " + t.get("agentNom", String.class) + " sans numéro WhatsApp";
        }
        if (t.get("contactId") == null) {
            return "Client sans numéro WhatsApp";
        }
        if (t.get("campagneEnAttente") != null) {
            return enAttente(t);
        }
        return null;
    }

    /** SMS : il part sous le nom du fournisseur, l'agent n'a pas besoin de numero WhatsApp. */
    private String motifExclusionSms(Tuple t) {
        if (t.get("agentId") == null) {
            return "Client sans agent de recouvrement";
        }
        if (!"ACTIF".equals(t.get("agentStatut", String.class))) {
            return "Agent " + t.get("agentNom", String.class) + " désactivé";
        }
        if (t.get("contactId") == null) {
            return "Client sans numéro de téléphone";
        }
        if (t.get("campagneEnAttente") != null) {
            return enAttente(t);
        }
        return null;
    }

    /** E-mail : il part de l'adresse d'UBIPHARM, l'agent n'a besoin ni de numero ni d'adresse. */
    private String motifExclusionEmail(Tuple t, String adresse) {
        if (t.get("agentId") == null) {
            return "Client sans agent de recouvrement";
        }
        if (!"ACTIF".equals(t.get("agentStatut", String.class))) {
            return "Agent " + t.get("agentNom", String.class) + " désactivé";
        }
        if (adresse == null) {
            return t.get("clientEmail") == null || t.get("clientEmail", String.class).isBlank()
                    ? "Client sans adresse e-mail" : "Adresse e-mail du client invalide";
        }
        if (t.get("campagneEnAttente") != null) {
            return enAttente(t);
        }
        return null;
    }

    private String enAttente(Tuple t) {
        return "Déjà dans la campagne « " + t.get("campagneEnAttente", String.class) + " » en attente d'envoi";
    }

    /** Premiere adresse valide d'un champ qui peut en contenir plusieurs (separees par ; ou ,). */
    static String premiereAdresse(String texte) {
        if (texte == null) {
            return null;
        }
        for (String morceau : texte.split("[;,\\s]+")) {
            if (EMAIL.matcher(morceau).matches()) {
                return morceau.toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    /** 2250708123456 devient 07 08 12 34 56 : le client le lit ou l'appelle tel quel. */
    static String numeroLisible(String numero) {
        if (numero == null) {
            return "";
        }
        String chiffres = numero.replaceAll("\\D", "");
        if (chiffres.length() == 13 && chiffres.startsWith("225")) {
            chiffres = chiffres.substring(3);
        }
        return chiffres.length() == 10 ? chiffres.replaceAll("(\\d{2})(?!$)", "$1 ") : numero;
    }

    private String valeur(Tuple t, String source, Map<String, String> aliasParSource) {
        int ecart = ((Number) t.get("ecart")).intValue();
        return switch (source) {
            case "calcul.agence" -> t.get("agence", String.class);
            case "calcul.segment" -> t.get("segment", String.class);
            case "calcul.sous_type" -> t.get("sousType", String.class);
            case "calcul.jours_retard" -> String.valueOf(Math.max(ecart, 0));
            case "calcul.jours_avant_echeance" -> String.valueOf(Math.max(-ecart, 0));
            case "calcul.agent_nom", "calcul.expediteur_nom" -> t.get("agentNom", String.class);
            case "calcul.agent_numero", "calcul.expediteur_numero" -> numeroLisible(t.get("agentNumero", String.class));
            default -> {
                String alias = aliasParSource.get(source);
                yield alias == null ? "" : formater(t.get(alias));
            }
        };
    }

    /** Dates en jj/mm/aaaa, montants avec separateur de milliers et sans decimales inutiles. */
    static String formater(Object valeur) {
        return switch (valeur) {
            case null -> "";
            case LocalDate d -> d.format(DATE_LISIBLE);
            case LocalDateTime d -> d.toLocalDate().format(DATE_LISIBLE);
            case java.sql.Date d -> d.toLocalDate().format(DATE_LISIBLE);
            case java.sql.Timestamp d -> d.toLocalDateTime().toLocalDate().format(DATE_LISIBLE);
            case BigDecimal b -> montant(b);
            default -> valeur.toString();
        };
    }

    static String montant(BigDecimal montant) {
        DecimalFormatSymbols symboles = new DecimalFormatSymbols();
        symboles.setGroupingSeparator(' ');
        symboles.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", symboles).format(montant);
    }

    private static void filtre(StringBuilder sql, Map<String, Object> parametres, String condition, String nom, Object valeur) {
        if (valeur == null || valeur instanceof List<?> liste && liste.isEmpty()) {
            return;
        }
        sql.append(" and ").append(condition);
        parametres.put(nom, valeur);
    }
}
