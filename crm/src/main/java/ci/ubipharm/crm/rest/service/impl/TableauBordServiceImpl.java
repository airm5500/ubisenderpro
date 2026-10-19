package ci.ubipharm.crm.rest.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.PerimetreService;
import ci.ubipharm.crm.rest.service.PortefeuilleService;
import ci.ubipharm.crm.rest.service.TableauBordService;
import ci.ubipharm.crm.rest.service.dto.CampagneResumeDTO;
import ci.ubipharm.crm.rest.service.dto.IndicateursDTO;
import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;
import ci.ubipharm.crm.rest.service.dto.RepartitionDTO;
import ci.ubipharm.crm.rest.service.dto.TableauBordDTO;
import ci.ubipharm.crm.rest.service.dto.TableauBordMarketingDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TableauBordServiceImpl implements TableauBordService {

    private static final String ENVOYES = "('ENVOYE', 'DISTRIBUE', 'LU')";
    private static final String TENTES = "('ENVOYE', 'DISTRIBUE', 'LU', 'ECHEC')";
    private static final String AFFECTATION = " left join crm_affectation af on af.client_id = c.id and af.date_fin is null";
    private static final String TOTAL = "cast(0 as signed)";
    private static final Long CLE_TOTAL = 0L;
    private static final String[] TRANCHES = {"Non échues", "Échues de 1 à 30 jours", "De 31 à 60 jours", "De 61 à 90 jours", "Plus de 90 jours"};

    private final EntityManager em;
    private final PerimetreService perimetreService;
    private final PortefeuilleService portefeuilleService;
    private final ParametreService parametreService;

    /** Conditions du perimetre sur les clients (alias c, af = affectation en cours) et sur les messages (alias c, m). */
    private record Filtre(String clients, String messages, String campagnes, Map<String, Object> parametres) {
    }

    @Override
    @Transactional(readOnly = true)
    public TableauBordDTO recouvrement(LocalDate du, LocalDate au, Long agenceId, Long agentId) {
        LocalDate jour = LocalDate.now(ZoneOffset.UTC);
        LocalDate fin = au == null ? jour : au;
        LocalDate debut = du == null ? fin.minusDays(29) : du;
        verifierPeriode(debut, fin);
        PerimetreDTO perimetre = perimetreService.perimetreCourant();
        List<Long> agents = portefeuilleService.agentsGeres();
        Filtre f = filtre(perimetre, agents, agenceId, agentId, debut, fin, jour);

        Map<Long, IndicateursDTO> total = indicateurs(f, TOTAL, TOTAL);
        IndicateursDTO indicateursTotal = total.getOrDefault(CLE_TOTAL, vide(CLE_TOTAL));
        indicateursTotal.setLibelle("Total");
        int joursProche = parametreService.entier("tableau_bord.echeance_proche_jours", 7);
        f.parametres().put("proche", jour.plusDays(joursProche));
        Tuple stock = une(f, """
                select coalesce(sum(f.date_echeance between :jour and :proche), 0) as nbProches,
                       coalesce(sum(case when f.date_echeance between :jour and :proche then f.solde end), 0) as soldeProche,
                       coalesce(sum(f.suivi = 'PROMESSE' and f.suivi_date >= :jour), 0) as nbPromesses,
                       coalesce(sum(case when f.suivi = 'PROMESSE' and f.suivi_date >= :jour then coalesce(f.suivi_montant, f.solde) end), 0) as montantPromis,
                       coalesce(sum(f.suivi = 'PROMESSE' and f.suivi_date < :jour), 0) as nonTenues,
                       coalesce(sum(f.suivi = 'LITIGE'), 0) as litiges, coalesce(sum(f.suivi = 'CONTENTIEUX'), 0) as contentieux
                from crm_facture f join crm_client c on c.id = f.client_id""" + AFFECTATION + """

                where f.nature = 'FACTURE' and f.solde > 0 and""" + f.clients());
        f.parametres().remove("proche");
        Tuple campagnes = une(f, """
                select coalesce(sum(cp.statut = 'A_VALIDER'), 0) as aValider, coalesce(sum(cp.statut = 'VALIDEE'), 0) as programmees,
                       coalesce(sum(cp.statut = 'EN_COURS'), 0) as enCours,
                       coalesce(sum(cp.statut = 'TERMINEE' and cp.termine_le >= :du and cp.termine_le < :fin), 0) as terminees,
                       coalesce(sum(cp.statut = 'ANNULEE' and cp.annule_le >= :du and cp.annule_le < :fin), 0) as annulees
                from crm_campagne cp where cp.module = 'RECOUVREMENT' and""" + f.campagnes());

        List<IndicateursDTO> parAgent = new ArrayList<>(indicateurs(f, "af.agent_id", "m.agent_id").values());
        Map<Long, String> noms = libelles("select id as id, nom_complet as libelle from crm_utilisateur");
        parAgent.forEach(i -> i.setLibelle(i.getId() == null ? "Clients sans agent" : noms.getOrDefault(i.getId(), "Agent " + i.getId())));
        parAgent.sort(Comparator.comparing(IndicateursDTO::getSolde).reversed());
        List<IndicateursDTO> parAgence = List.of();
        if (agenceId == null && agents == null && (perimetre.isVisionGlobale() || perimetre.getAgenceIds().size() > 1)) {
            parAgence = new ArrayList<>(indicateurs(f, "c.agence_id", "c.agence_id").values());
            Map<Long, String> agences = libelles("select id as id, libelle as libelle from crm_agence");
            parAgence.forEach(i -> i.setLibelle(agences.getOrDefault(i.getId(), "Agence " + i.getId())));
            parAgence.sort(Comparator.comparing(IndicateursDTO::getLibelle));
        }

        return TableauBordDTO.builder()
                .du(debut).au(fin)
                .vue(agents != null ? "AGENT" : perimetre.isVisionGlobale() ? "GLOBAL" : "AGENCE")
                .total(indicateursTotal)
                .joursEcheanceProche(joursProche)
                .nbEcheanceProche(nombre(stock, "nbProches"))
                .soldeEcheanceProche(montant(stock, "soldeProche"))
                .nbPromessesEnCours(nombre(stock, "nbPromesses"))
                .montantPromis(montant(stock, "montantPromis"))
                .nbPromessesNonTenues(nombre(stock, "nonTenues"))
                .nbLitiges(nombre(stock, "litiges"))
                .nbContentieux(nombre(stock, "contentieux"))
                .campagnesAValider(nombre(campagnes, "aValider"))
                .campagnesProgrammees(nombre(campagnes, "programmees"))
                .campagnesEnCours(nombre(campagnes, "enCours"))
                .campagnesTerminees(nombre(campagnes, "terminees"))
                .campagnesAnnulees(nombre(campagnes, "annulees"))
                .parSousType(repartition(f, """
                        select st.code as code, st.libelle as libelle, count(*) as nombre, coalesce(sum(f.solde), 0) as montant
                        from crm_facture f join crm_client c on c.id = f.client_id join crm_sous_type_compte st on st.id = f.sous_type_id""" + AFFECTATION + """

                        where f.nature = 'FACTURE' and f.solde > 0 and""" + f.clients() + " group by st.code, st.libelle order by montant desc"))
                .balanceAgee(balanceAgee(f))
                .echecs(repartition(f, """
                        select null as code, coalesce(m.motif, 'Sans motif') as libelle, count(*) as nombre, null as montant
                        from crm_campagne_message m join crm_campagne cp on cp.id = m.campagne_id and cp.module = 'RECOUVREMENT'
                        join crm_client c on c.id = m.client_id
                        where m.statut = 'ECHEC' and coalesce(m.envoye_le, m.updated_at) >= :du and coalesce(m.envoye_le, m.updated_at) < :fin
                          and""" + f.messages() + " group by coalesce(m.motif, 'Sans motif') order by nombre desc limit 8"))
                .annulations(repartition(f, """
                        select cast(cp.id as char) as code, concat(cp.nom, ' : ', coalesce(cp.motif_annulation, 'sans motif')) as libelle,
                               cp.nb_messages as nombre, null as montant
                        from crm_campagne cp
                        where cp.module = 'RECOUVREMENT' and cp.statut = 'ANNULEE' and cp.annule_par is null
                          and cp.annule_le >= :du and cp.annule_le < :fin and""" + f.campagnes() + " order by cp.annule_le desc limit 5"))
                .parAgent(parAgent)
                .parAgence(parAgence)
                .build();
    }

    /** Indicateurs regroupes par cle (total, agent ou agence) ; la cle des messages est l'agent qui a envoye. */
    private Map<Long, IndicateursDTO> indicateurs(Filtre f, String cleClient, String cleMessage) {
        Map<Long, IndicateursDTO> resultat = new LinkedHashMap<>();
        cumuler(resultat, f, """
                select %s as cle, count(distinct c.id) as nbClients, count(f.id) as nbFactures, coalesce(sum(f.solde), 0) as solde,
                       coalesce(sum(f.date_echeance < :jour), 0) as nbEchues,
                       coalesce(sum(case when f.date_echeance < :jour then f.solde end), 0) as soldeEchu
                from crm_client c%s
                left join crm_facture f on f.client_id = c.id and f.nature = 'FACTURE' and f.solde > 0
                where%s group by cle
                """.formatted(cleClient, AFFECTATION, f.clients()), (i, t) -> {
                    i.setNbClients(nombre(t, "nbClients"));
                    i.setNbFactures(nombre(t, "nbFactures"));
                    i.setSolde(montant(t, "solde"));
                    i.setNbEchues(nombre(t, "nbEchues"));
                    i.setSoldeEchu(montant(t, "soldeEchu"));
                });
        String messages = """
                from crm_campagne_message m join crm_campagne cp on cp.id = m.campagne_id and cp.module = 'RECOUVREMENT'
                join crm_client c on c.id = m.client_id
                where coalesce(m.envoye_le, m.updated_at) >= :du and coalesce(m.envoye_le, m.updated_at) < :fin and""" + f.messages();
        cumuler(resultat, f, """
                select %s as cle, coalesce(sum(m.statut in %s), 0) as tentes, coalesce(sum(m.statut in %s), 0) as envoyes,
                       coalesce(sum(m.statut in ('DISTRIBUE', 'LU')), 0) as distribues, coalesce(sum(m.statut = 'LU'), 0) as lus,
                       coalesce(sum(m.statut = 'ECHEC'), 0) as echecs,
                       count(distinct case when m.statut in %s then m.facture_id end) as facturesRelancees,
                       count(distinct case when m.statut in %s then m.client_id end) as clientsRelances
                %s and m.statut in %s group by cle
                """.formatted(cleMessage, TENTES, ENVOYES, ENVOYES, ENVOYES, messages, TENTES), (i, t) -> {
                    i.setMessagesTentes(nombre(t, "tentes"));
                    i.setMessagesEnvoyes(nombre(t, "envoyes"));
                    i.setMessagesDistribues(nombre(t, "distribues"));
                    i.setMessagesLus(nombre(t, "lus"));
                    i.setMessagesEchecs(nombre(t, "echecs"));
                    i.setFacturesRelancees(nombre(t, "facturesRelancees"));
                    i.setClientsRelances(nombre(t, "clientsRelances"));
                });
        // Conversion : facture relancee dans la periode dont le solde a baisse apres la premiere relance
        cumuler(resultat, f, """
                select r.cle as cle, count(*) as converties from (
                    select %s as cle, m.facture_id as facture, min(m.envoye_le) as premier
                    %s and m.statut in %s group by 1, 2) r
                where exists (select 1 from crm_facture_solde s where s.facture_id = r.facture and s.nouveau_solde < s.ancien_solde
                              and s.created_at >= r.premier)
                group by r.cle
                """.formatted(cleMessage, messages, ENVOYES), (i, t) -> i.setFacturesConverties(nombre(t, "converties")));
        cumuler(resultat, f, """
                select %s as cle, count(*) as reponses, count(distinct r.client_id) as clients
                from crm_message_recu r join crm_client c on c.id = r.client_id%s
                where r.recu_le >= :du and r.recu_le < :fin and not exists (select 1 from crm_campagne_message x
                      join crm_campagne y on y.id = x.campagne_id where x.id = r.campagne_message_id and y.module = 'MARKETING')
                  and%s group by cle
                """.formatted(cleClient, AFFECTATION, f.clients()), (i, t) -> {
                    i.setReponses(nombre(t, "reponses"));
                    i.setClientsAyantRepondu(nombre(t, "clients"));
                });
        cumuler(resultat, f, """
                select %s as cle, count(*) as promesses
                from crm_facture_suivi s join crm_facture f on f.id = s.facture_id join crm_client c on c.id = f.client_id%s
                where s.suivi = 'PROMESSE' and s.created_at >= :du and s.created_at < :fin and%s group by cle
                """.formatted(cleClient, AFFECTATION, f.clients()), (i, t) -> i.setPromesses(nombre(t, "promesses")));
        cumuler(resultat, f, """
                select %s as cle,
                       coalesce(sum(case when s.nouveau_solde < s.ancien_solde then s.ancien_solde - greatest(s.nouveau_solde, 0) end), 0) as encaisse,
                       count(distinct case when s.nouveau_solde <= 0 and s.ancien_solde > 0 then s.facture_id end) as soldees
                from crm_facture_solde s join crm_facture f on f.id = s.facture_id and f.nature = 'FACTURE'
                join crm_client c on c.id = f.client_id%s
                where s.created_at >= :du and s.created_at < :fin and%s group by cle
                """.formatted(cleClient, AFFECTATION, f.clients()), (i, t) -> {
                    i.setMontantEncaisse(montant(t, "encaisse"));
                    i.setFacturesSoldees(nombre(t, "soldees"));
                });
        resultat.values().forEach(i -> {
            i.setTauxReussite(taux(i.getMessagesEnvoyes(), i.getMessagesTentes()));
            i.setTauxEchec(taux(i.getMessagesEchecs(), i.getMessagesTentes()));
            i.setTauxReponse(taux(Math.min(i.getClientsAyantRepondu(), i.getClientsRelances()), i.getClientsRelances()));
            i.setTauxConversion(taux(i.getFacturesConverties(), i.getFacturesRelancees()));
        });
        return resultat;
    }

    private void cumuler(Map<Long, IndicateursDTO> resultat, Filtre f, String sql, BiConsumer<IndicateursDTO, Tuple> application) {
        for (Tuple t : lignes(f, sql)) {
            Number cle = (Number) t.get("cle");
            Long id = cle == null ? null : cle.longValue();
            application.accept(resultat.computeIfAbsent(id, TableauBordServiceImpl::vide), t);
        }
    }

    private List<RepartitionDTO> balanceAgee(Filtre f) {
        Map<String, RepartitionDTO> tranches = new LinkedHashMap<>();
        for (int i = 0; i < TRANCHES.length; i++) {
            tranches.put(String.valueOf(i), RepartitionDTO.builder().code(String.valueOf(i)).libelle(TRANCHES[i]).montant(BigDecimal.ZERO).build());
        }
        for (Tuple t : lignes(f, """
                select case when datediff(:jour, f.date_echeance) <= 0 then '0' when datediff(:jour, f.date_echeance) <= 30 then '1'
                            when datediff(:jour, f.date_echeance) <= 60 then '2' when datediff(:jour, f.date_echeance) <= 90 then '3' else '4' end as tranche,
                       count(*) as nombre, coalesce(sum(f.solde), 0) as montant
                from crm_facture f join crm_client c on c.id = f.client_id""" + AFFECTATION + """

                where f.nature = 'FACTURE' and f.solde > 0 and""" + f.clients() + " group by tranche")) {
            RepartitionDTO tranche = tranches.get(t.get("tranche").toString());
            tranche.setNombre(nombre(t, "nombre"));
            tranche.setMontant(montant(t, "montant"));
        }
        return new ArrayList<>(tranches.values());
    }

    private Filtre filtre(PerimetreDTO perimetre, List<Long> agents, Long agenceId, Long agentId, LocalDate debut, LocalDate fin, LocalDate jour) {
        StringBuilder clients = new StringBuilder(" c.statut = 'ACTIF'");
        StringBuilder messages = new StringBuilder(" 1 = 1");
        Map<String, Object> parametres = new HashMap<>();
        parametres.put("du", debut.atStartOfDay());
        parametres.put("fin", fin.plusDays(1).atStartOfDay());
        parametres.put("jour", jour);
        if (!perimetre.isVisionGlobale()) {
            if (perimetre.getAgenceIds().isEmpty()) {
                return new Filtre(" 1 = 0", " 1 = 0", " 1 = 0", parametres);
            }
            clients.append(" and c.agence_id in (:agences)");
            messages.append(" and c.agence_id in (:agences)");
            parametres.put("agences", perimetre.getAgenceIds());
        }
        if (agents != null) {
            clients.append(" and af.agent_id in (:agents)");
            messages.append(" and m.agent_id in (:agents)");
            parametres.put("agents", agents);
        }
        if (agenceId != null) {
            clients.append(" and c.agence_id = :agenceId");
            messages.append(" and c.agence_id = :agenceId");
            parametres.put("agenceId", agenceId);
        }
        if (agentId != null) {
            clients.append(" and af.agent_id = :agentId");
            messages.append(" and m.agent_id = :agentId");
            parametres.put("agentId", agentId);
        }
        String campagnes = " 1 = 1";
        if (agents != null || agentId != null || agenceId != null || !perimetre.isVisionGlobale()) {
            // Campagnes creees par les agents suivis, ou qui relancent des clients du perimetre
            campagnes = " (exists (select 1 from crm_campagne_message m join crm_client c on c.id = m.client_id where m.campagne_id = cp.id and"
                    + messages + ")";
            if (agents != null || agentId != null) {
                campagnes += " or cp.cree_par in (:createurs)";
                parametres.put("createurs", agentId != null ? List.of(agentId) : agents);
            }
            campagnes += ")";
        }
        return new Filtre(clients.toString(), messages.toString(), campagnes, parametres);
    }

    @Override
    @Transactional(readOnly = true)
    public TableauBordMarketingDTO marketing(LocalDate du, LocalDate au) {
        LocalDate jour = LocalDate.now(ZoneOffset.UTC);
        LocalDate fin = au == null ? jour : au;
        LocalDate debut = du == null ? fin.minusDays(29) : du;
        verifierPeriode(debut, fin);
        Filtre f = filtre(perimetreService.perimetreCourant(), null, null, null, debut, fin, jour);
        String messages = """
                from crm_campagne_message m join crm_campagne cp on cp.id = m.campagne_id and cp.module = 'MARKETING'
                join crm_client c on c.id = m.client_id
                where m.statut in %s and coalesce(m.envoye_le, m.updated_at) >= :du and coalesce(m.envoye_le, m.updated_at) < :fin and%s
                """.formatted(TENTES, f.messages());
        Tuple envois = une(f, """
                select coalesce(sum(m.statut in %s), 0) as envoyes, count(*) as tentes,
                       coalesce(sum(m.statut in ('DISTRIBUE', 'LU')), 0) as distribues, coalesce(sum(m.statut = 'LU'), 0) as lus,
                       coalesce(sum(m.statut = 'ECHEC'), 0) as echecs, count(distinct case when m.statut in %s then m.client_id end) as clients
                %s""".formatted(ENVOYES, ENVOYES, messages));
        Tuple reponses = une(f, """
                select count(*) as reponses, count(distinct r.client_id) as clients
                from crm_message_recu r join crm_campagne_message m on m.id = r.campagne_message_id
                join crm_campagne cp on cp.id = m.campagne_id and cp.module = 'MARKETING' join crm_client c on c.id = r.client_id
                where r.recu_le >= :du and r.recu_le < :fin and""" + f.messages());
        Tuple campagnes = une(f, """
                select coalesce(sum(cp.statut = 'A_VALIDER'), 0) as aValider, coalesce(sum(cp.statut = 'VALIDEE'), 0) as programmees,
                       coalesce(sum(cp.statut = 'EN_COURS'), 0) as enCours,
                       coalesce(sum(cp.statut = 'TERMINEE' and cp.termine_le >= :du and cp.termine_le < :fin), 0) as terminees,
                       coalesce(sum(cp.statut = 'ANNULEE' and cp.annule_le >= :du and cp.annule_le < :fin), 0) as annulees
                from crm_campagne cp where cp.module = 'MARKETING' and""" + f.campagnes());
        Tuple base = une(f, """
                select (select count(*) from crm_client c where c.desabonne_marketing_le >= :du and c.desabonne_marketing_le < :fin and%s) as desabonnements,
                       (select count(*) from crm_promotion p where p.statut = 'ACTIVE' and :jour between p.date_debut and p.date_fin) as promotions,
                       (select count(*) from crm_liste_diffusion l where l.statut = 'ACTIF') as listes,
                       (select count(*) from crm_client c where c.desabonne_marketing_le is null and%s and exists (select 1 from crm_contact x
                            where x.client_id = c.id and x.statut = 'ACTIF' and x.numero_whatsapp is not null)) as joignables
                """.formatted(f.clients(), f.clients()));
        @SuppressWarnings("unchecked")
        List<Tuple> lancees = requete(f, """
                select cp.id as id, cp.nom as nom, cp.statut as statut, mo.canal as canal, cp.lance_le as lanceLe,
                       (select count(*) from crm_campagne_message m where m.campagne_id = cp.id and m.statut in %s) as envoyes,
                       (select count(*) from crm_campagne_message m where m.campagne_id = cp.id and m.statut = 'LU') as lus,
                       (select count(*) from crm_campagne_message m where m.campagne_id = cp.id and m.statut = 'ECHEC') as echecs,
                       (select count(*) from crm_message_recu r join crm_campagne_message m on m.id = r.campagne_message_id
                        where m.campagne_id = cp.id) as reponses
                from crm_campagne cp join crm_modele_message mo on mo.id = cp.modele_id
                where cp.module = 'MARKETING' and cp.lance_le >= :du and cp.lance_le < :fin and%s
                order by cp.lance_le desc limit 10
                """.formatted(ENVOYES, f.campagnes())).getResultList();
        long tentes = nombre(envois, "tentes");
        long envoyes = nombre(envois, "envoyes");
        long clients = nombre(envois, "clients");
        long repondants = Math.min(nombre(reponses, "clients"), clients);
        return TableauBordMarketingDTO.builder()
                .du(debut).au(fin)
                .campagnesAValider(nombre(campagnes, "aValider"))
                .campagnesProgrammees(nombre(campagnes, "programmees"))
                .campagnesEnCours(nombre(campagnes, "enCours"))
                .campagnesTerminees(nombre(campagnes, "terminees"))
                .campagnesAnnulees(nombre(campagnes, "annulees"))
                .messagesTentes(tentes)
                .messagesEnvoyes(envoyes)
                .messagesDistribues(nombre(envois, "distribues"))
                .messagesLus(nombre(envois, "lus"))
                .messagesEchecs(nombre(envois, "echecs"))
                .clientsTouches(clients)
                .reponses(nombre(reponses, "reponses"))
                .clientsAyantRepondu(repondants)
                .desabonnements(nombre(base, "desabonnements"))
                .tauxReussite(taux(envoyes, tentes))
                .tauxLecture(taux(nombre(envois, "lus"), envoyes))
                .tauxReponse(taux(repondants, clients))
                .promotionsEnCours(nombre(base, "promotions"))
                .listesActives(nombre(base, "listes"))
                .clientsJoignables(nombre(base, "joignables"))
                .parCanal(repartition(f, """
                        select m.canal as code, m.canal as libelle, coalesce(sum(m.statut in %s), 0) as nombre,
                               coalesce(sum(m.statut = 'LU'), 0) as montant
                        %s group by m.canal order by nombre desc""".formatted(ENVOYES, messages)))
                .echecs(repartition(f, """
                        select null as code, coalesce(m.motif, 'Sans motif') as libelle, count(*) as nombre, null as montant
                        %s and m.statut = 'ECHEC' group by coalesce(m.motif, 'Sans motif') order by nombre desc limit 8""".formatted(messages)))
                .campagnes(lancees.stream().map(t -> CampagneResumeDTO.builder()
                        .id(((Number) t.get("id")).longValue())
                        .nom(t.get("nom", String.class))
                        .statut(t.get("statut", String.class))
                        .canal(t.get("canal", String.class))
                        .lanceLe(t.get("lanceLe", LocalDateTime.class))
                        .envoyes(nombre(t, "envoyes"))
                        .lus(nombre(t, "lus"))
                        .echecs(nombre(t, "echecs"))
                        .reponses(nombre(t, "reponses"))
                        .build()).toList())
                .build();
    }

    private static void verifierPeriode(LocalDate debut, LocalDate fin) {
        if (debut.isAfter(fin)) {
            throw new ErreurMetierException("Le début de la période doit précéder sa fin.");
        }
        if (ChronoUnit.DAYS.between(debut, fin) > 366) {
            throw new ErreurMetierException("La période ne peut pas dépasser un an.");
        }
    }

    private List<RepartitionDTO> repartition(Filtre f, String sql) {
        return lignes(f, sql).stream().map(t -> RepartitionDTO.builder()
                .code(t.get("code") == null ? null : t.get("code").toString())
                .libelle(t.get("libelle", String.class))
                .nombre(nombre(t, "nombre"))
                .montant(t.get("montant") == null ? null : montant(t, "montant"))
                .build()).toList();
    }

    private Map<Long, String> libelles(String sql) {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery(sql, Tuple.class).getResultList();
        Map<Long, String> resultat = new HashMap<>();
        lignes.forEach(t -> resultat.put(((Number) t.get("id")).longValue(), t.get("libelle", String.class)));
        return resultat;
    }

    private Tuple une(Filtre f, String sql) {
        return lignes(f, sql).getFirst();
    }

    @SuppressWarnings("unchecked")
    private List<Tuple> lignes(Filtre f, String sql) {
        return requete(f, sql).getResultList();
    }

    /** Ne lie que les parametres presents dans la requete. */
    private Query requete(Filtre f, String sql) {
        Query requete = em.createNativeQuery(sql, Tuple.class);
        f.parametres().forEach((nom, valeur) -> {
            if (sql.contains(":" + nom)) {
                requete.setParameter(nom, valeur);
            }
        });
        return requete;
    }

    private static IndicateursDTO vide(Long id) {
        return IndicateursDTO.builder().id(id).solde(BigDecimal.ZERO).soldeEchu(BigDecimal.ZERO).montantEncaisse(BigDecimal.ZERO).build();
    }

    private static long nombre(Tuple t, String alias) {
        Object valeur = t.get(alias);
        return valeur == null ? 0 : ((Number) valeur).longValue();
    }

    private static BigDecimal montant(Tuple t, String alias) {
        Object valeur = t.get(alias);
        return valeur == null ? BigDecimal.ZERO : new BigDecimal(valeur.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private static Double taux(long part, long total) {
        return total == 0 ? null : BigDecimal.valueOf(part * 100.0 / total).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
