package ci.ubipharm.crm.rest.service.impl;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.rest.service.SupportSanteService;
import ci.ubipharm.crm.rest.service.dto.SupportSondeDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.extern.slf4j.Slf4j;

/** Chaque sonde lit l'etat sans rien modifier ; une sonde en echec est rendue KO sans interrompre les autres. */
@Slf4j
@Service
public class SupportSanteServiceImpl implements SupportSanteService {

    private static final java.util.Map<String, String> LIBELLES_SESSION = java.util.Map.of(
            "DECONNECTE", "déconnecté(s)", "EN_ATTENTE_QR", "en attente du QR code", "CONNEXION", "en cours de connexion");


    static final String OK = "OK";
    static final String ALERTE = "ALERTE";
    static final String KO = "KO";

    private final EntityManager em;
    private final ParametreService parametreService;
    private final PasserelleWhatsappService passerelleWhatsappService;
    private final String serveurSmtp;
    private final String portSmtp;

    public SupportSanteServiceImpl(EntityManager em, ParametreService parametreService, PasserelleWhatsappService passerelleWhatsappService,
            @Value("${spring.mail.host:}") String serveurSmtp, @Value("${spring.mail.port:}") String portSmtp) {
        this.em = em;
        this.parametreService = parametreService;
        this.passerelleWhatsappService = passerelleWhatsappService;
        this.serveurSmtp = serveurSmtp;
        this.portSmtp = portSmtp;
    }

    /** Etat et explication rendus par une sonde. */
    private record Resultat(String etat, String detail) {
    }

    @Override
    public List<SupportSondeDTO> sonder() {
        List<SupportSondeDTO> sondes = new ArrayList<>();
        sondes.add(sonde("BASE", "Base de données", this::base));
        sondes.add(sonde("WHATSAPP_WEB", "WhatsApp Web", this::whatsappWeb));
        sondes.add(sonde("EMAIL", "E-mail (SMTP)", this::email));
        sondes.add(sonde("SMS", "Fournisseur SMS", this::sms));
        sondes.add(sonde("ARCHIVAGE", "Répertoire d'archivage", this::archivage));
        sondes.add(sonde("SERVEUR", "Serveur (mémoire, durée de fonctionnement)", this::serveur));
        return sondes;
    }

    private SupportSondeDTO sonde(String code, String libelle, Supplier<Resultat> mesure) {
        long debut = System.nanoTime();
        Resultat r;
        try {
            r = mesure.get();
        } catch (Throwable t) {
            log.warn("Sonde {} en échec : {}", code, t.toString());
            r = new Resultat(KO, "Sonde en échec : " + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : " - " + court(t.getMessage())));
        }
        return SupportSondeDTO.builder().code(code).libelle(libelle).etat(r.etat()).detail(r.detail())
                .dureeMs((System.nanoTime() - debut) / 1_000_000).build();
    }

    private Resultat base() {
        long debut = System.nanoTime();
        em.createNativeQuery("select 1").getSingleResult();
        long ms = (System.nanoTime() - debut) / 1_000_000;
        @SuppressWarnings("unchecked")
        List<String> versions = em.createNativeQuery(
                "select version from flyway_schema_history where success = 1 and version is not null order by installed_rank desc limit 1")
                .getResultList();
        String version = versions.isEmpty() ? "inconnue" : versions.getFirst();
        return new Resultat(OK, "Répond en " + ms + " ms · schéma en version " + version);
    }

    private Resultat whatsappWeb() {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select statut as statut, count(*) as nb, sum(case when sante = 'DEGRADED' then 1 else 0 end) as degradees
                from crm_session_whatsapp where fournisseur = 'WA_WEB' group by statut
                """, Tuple.class).getResultList();
        Map<String, Long> parStatut = new LinkedHashMap<>();
        long total = 0;
        long degradees = 0;
        for (Tuple t : lignes) {
            long nb = ((Number) t.get("nb")).longValue();
            parStatut.put(t.get("statut", String.class), nb);
            total += nb;
            degradees += t.get("degradees") == null ? 0 : ((Number) t.get("degradees")).longValue();
        }
        long connectees = parStatut.getOrDefault("CONNECTE", 0L);
        StringBuilder detail = new StringBuilder();
        boolean repond = passerelleWhatsappService.joignable();
        detail.append(repond ? "Service wa-web joignable" : "Service wa-web injoignable");
        if (total == 0) {
            detail.append(" · aucun numéro WhatsApp Web enregistré");
            return new Resultat(repond ? OK : ALERTE, detail.toString());
        }
        detail.append(" · ").append(connectees).append(" numéro(s) connecté(s) sur ").append(total);
        parStatut.forEach((statut, nb) -> {
            if (!"CONNECTE".equals(statut)) {
                detail.append(", ").append(nb).append(' ').append(LIBELLES_SESSION.getOrDefault(statut, statut.toLowerCase()));
            }
        });
        if (degradees > 0) {
            detail.append(" · ").append(degradees).append(" session(s) dégradée(s)");
        }
        if (!repond) {
            return new Resultat(KO, detail.toString());
        }
        return new Resultat(connectees == total && degradees == 0 ? OK : ALERTE, detail.toString());
    }

    private Resultat email() {
        if (serveurSmtp == null || serveurSmtp.isBlank()) {
            return new Resultat(ALERTE, "Serveur SMTP non configuré (spring.mail.host) : aucun e-mail ne part.");
        }
        return new Resultat(OK, "Serveur SMTP configuré : " + serveurSmtp + (portSmtp == null || portSmtp.isBlank() ? "" : ":" + portSmtp));
    }

    private Resultat sms() {
        @SuppressWarnings("unchecked")
        List<Tuple> lignes = em.createNativeQuery("""
                select libelle as libelle, actif as actif, case when cle_api is null or trim(cle_api) = '' then 0 else 1 end as cle
                from crm_fournisseur_sms where par_defaut = true order by id limit 1
                """, Tuple.class).getResultList();
        if (lignes.isEmpty()) {
            return new Resultat(ALERTE, "Aucun fournisseur SMS par défaut : choisissez-le dans Fournisseurs SMS.");
        }
        Tuple f = lignes.getFirst();
        String libelle = f.get("libelle", String.class);
        if (!Boolean.TRUE.equals(f.get("actif", Boolean.class))) {
            return new Resultat(ALERTE, "Le fournisseur par défaut " + libelle + " est désactivé.");
        }
        if (((Number) f.get("cle")).intValue() == 0) {
            return new Resultat(ALERTE, "Clé API du fournisseur " + libelle + " non saisie : aucun SMS ne part.");
        }
        return new Resultat(OK, "Fournisseur par défaut " + libelle + " actif, clé API saisie.");
    }

    private Resultat archivage() {
        if (!parametreService.oui("archivage.actif", true)) {
            return new Resultat(OK, "Archivage désactivé dans les Paramètres.");
        }
        String repertoire = parametreService.valeur("archivage.repertoire", null);
        if (repertoire == null) {
            return new Resultat(ALERTE, "Répertoire d'archivage non renseigné dans les Paramètres.");
        }
        File dossier = new File(repertoire);
        if (!dossier.isDirectory()) {
            return new Resultat(KO, "Le répertoire " + repertoire + " n'existe pas sur le serveur.");
        }
        if (!dossier.canWrite()) {
            return new Resultat(KO, "Le répertoire " + repertoire + " n'est pas accessible en écriture.");
        }
        long libreMo = dossier.getUsableSpace() / (1024 * 1024);
        int minimumMo = parametreService.entier("support.espace_disque_min_mo", 1024);
        String detail = repertoire + " · " + formaterMo(libreMo) + " libres";
        return new Resultat(libreMo < minimumMo ? ALERTE : OK, libreMo < minimumMo ? detail + " (minimum " + formaterMo(minimumMo) + ")" : detail);
    }

    private Resultat serveur() {
        Runtime jvm = Runtime.getRuntime();
        long maxMo = jvm.maxMemory() / (1024 * 1024);
        long utiliseeMo = (jvm.totalMemory() - jvm.freeMemory()) / (1024 * 1024);
        Duration duree = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
        String detail = "Mémoire " + utiliseeMo + " Mo utilisés sur " + maxMo + " Mo · démarré depuis " + duree.toDays() + " j "
                + duree.toHoursPart() + " h " + duree.toMinutesPart() + " min · Java " + Runtime.version().feature();
        return new Resultat(maxMo > 0 && utiliseeMo * 100 / maxMo >= 90 ? ALERTE : OK, detail);
    }

    private static String formaterMo(long mo) {
        return mo >= 1024 ? String.format("%.1f Go", mo / 1024.0) : mo + " Mo";
    }

    private static String court(String texte) {
        return texte.length() > 300 ? texte.substring(0, 300) + "…" : texte;
    }
}
