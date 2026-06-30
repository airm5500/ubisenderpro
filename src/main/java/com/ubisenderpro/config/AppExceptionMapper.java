package com.ubisenderpro.config;

import com.ubisenderpro.service.AuditService;
import com.ubisenderpro.service.ValidationException;

import javax.ejb.EJB;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mapper d'exceptions global : transforme toute erreur non gérée en réponse JSON
 * exploitable et la consigne précisément pour le dépannage (#3).
 *
 * <ul>
 *   <li><b>Logs techniques</b> : message précis avec chemin et champ fautif, pour
 *       que l'informaticien identifie la cause en un coup d'œil.</li>
 *   <li><b>Journal applicatif</b> : message clair et lisible par un utilisateur
 *       lambda (consultable dans Utilisateurs &gt; Journal d'actions).</li>
 *   <li><b>Réponse client</b> : {@code {erreur, champ}} pour afficher un message
 *       compréhensible et surligner le champ concerné.</li>
 * </ul>
 */
@Provider
public class AppExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(AppExceptionMapper.class.getName());

    @EJB
    private AuditService auditService;
    @Context
    private HttpHeaders headers;
    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable ex) {
        // Respecte les réponses JAX-RS explicites (404 introuvable, 401 non authentifié, ...).
        if (ex instanceof WebApplicationException) {
            return ((WebApplicationException) ex).getResponse();
        }

        String chemin = uriInfo != null ? uriInfo.getPath() : "?";
        String auth = headers != null ? headers.getHeaderString(HttpHeaders.AUTHORIZATION) : null;

        ValidationException ve = chercher(ex, ValidationException.class);
        IllegalArgumentException iae = ve == null ? chercher(ex, IllegalArgumentException.class) : null;

        if (ve != null || iae != null) {
            String champ = ve != null ? ve.getChamp() : null;
            String message = (ve != null ? ve.getMessage() : iae.getMessage());
            if (message == null || message.isEmpty()) { message = "Données invalides."; }

            // Log technique précis (champ + raison) pour faciliter le dépannage.
            LOG.warning("VALIDATION_KO chemin=/" + chemin
                    + (champ != null ? " champ=" + champ : "") + " raison=\"" + message + "\"");
            // Journal lisible pour un utilisateur non informaticien.
            auditService.tracer(auth, "VALIDATION_REFUS", "Saisie", null, message);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("erreur", message);
            if (champ != null) { body.put("champ", champ); }
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON).entity(body).build();
        }

        // Violation de contrainte BDD (NOT NULL, doublon, clé étrangère) : message clair
        // adapté au menu + log précis, plutôt qu'un message technique opaque.
        String sql = messageSql(ex);
        if (sql != null) {
            String menu = libelleMenu(chemin);
            String message = traduireSql(sql, menu);
            LOG.warning("CONTRAINTE_BDD chemin=/" + chemin + " menu=\"" + menu + "\" : " + sql);
            auditService.tracer(auth, "ERREUR_SAISIE", menu, null, message);
            Map<String, Object> corps = new LinkedHashMap<>();
            corps.put("erreur", message);
            Response.Status statut = sql.contains("Duplicate entry")
                    ? Response.Status.CONFLICT : Response.Status.BAD_REQUEST;
            return Response.status(statut).type(MediaType.APPLICATION_JSON).entity(corps).build();
        }

        // Erreur inattendue : trace complète côté serveur, message clair côté client.
        String menu = libelleMenu(chemin);
        LOG.log(Level.SEVERE, "ERREUR_SERVEUR chemin=/" + chemin + " menu=\"" + menu + "\" : " + ex.getMessage(), ex);
        auditService.tracer(auth, "ERREUR_SERVEUR", menu, null,
                "Erreur technique : " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("erreur", "L'opération sur « " + menu + " » a échoué pour une raison technique. "
                + "Réessayez ; si le problème persiste, contactez l'administrateur (le détail est dans le journal).");
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON).entity(body).build();
    }

    /** Recherche dans la chaîne des causes un message de violation de contrainte SQL. */
    private String messageSql(Throwable ex) {
        Throwable c = ex;
        int garde = 0;
        while (c != null && garde++ < 15) {
            String m = c.getMessage();
            if (m != null && (m.contains("cannot be null") || m.contains("Duplicate entry")
                    || m.contains("foreign key constraint fails")
                    || m.contains("Data too long") || m.contains("Incorrect"))) {
                return m;
            }
            c = c.getCause();
        }
        return null;
    }

    /** Traduit un message SQL technique en message utilisateur explicite. */
    private String traduireSql(String sql, String menu) {
        if (sql.contains("cannot be null")) {
            String col = entreApostrophes(sql);
            return "Le champ « " + libelleColonne(col) + " » est obligatoire (" + menu + ").";
        }
        if (sql.contains("Duplicate entry")) {
            return "Cette valeur existe déjà (" + menu + "). Vérifiez les champs uniques (code, numéro…).";
        }
        if (sql.contains("foreign key constraint fails")) {
            return "Opération impossible sur « " + menu + " » : un élément lié est manquant "
                    + "ou cet enregistrement est déjà utilisé ailleurs.";
        }
        if (sql.contains("Data too long")) {
            return "Une valeur saisie est trop longue (" + menu + "). Raccourcissez le champ concerné.";
        }
        return "Saisie invalide (" + menu + "). Vérifiez les champs renseignés.";
    }

    /** Premier texte entre apostrophes simples (ex. nom de colonne dans le message SQL). */
    private String entreApostrophes(String s) {
        int a = s.indexOf('\'');
        int b = a >= 0 ? s.indexOf('\'', a + 1) : -1;
        return (a >= 0 && b > a) ? s.substring(a + 1, b) : "?";
    }

    /** Libellé lisible d'une colonne (sinon underscores -> espaces). */
    private String libelleColonne(String col) {
        switch (col) {
            case "numero_client": return "Numéro client";
            case "nom_compte": return "Nom du compte";
            case "nom_complet": return "Nom complet";
            case "login": return "Login";
            case "libelle": return "Libellé";
            case "titre": return "Titre";
            case "code": return "Code";
            case "type": return "Type";
            default: return col == null ? "?" : col.replace('_', ' ');
        }
    }

    /** Libellé du menu déduit du chemin REST (pour un message adapté). */
    private String libelleMenu(String chemin) {
        String p = chemin == null ? "" : chemin.toLowerCase();
        if (p.startsWith("clients") || p.startsWith("contacts")) { return "Comptes clients"; }
        if (p.startsWith("catalogue") || p.startsWith("articles")) { return "Catalogue"; }
        if (p.startsWith("promotions")) { return "Promotions"; }
        if (p.startsWith("templates") || p.startsWith("propositions")) { return "Marketing"; }
        if (p.startsWith("dispo")) { return "Disponibilités & Ruptures"; }
        if (p.startsWith("infos")) { return "Informations Clients"; }
        if (p.startsWith("campaigns")) { return "Campagnes"; }
        if (p.startsWith("users") || p.startsWith("permissions")) { return "Utilisateurs"; }
        if (p.startsWith("referentiels")) { return "Référentiels"; }
        if (p.startsWith("segmentations") || p.startsWith("segments")) { return "Segmentations"; }
        if (p.startsWith("lists")) { return "Listes de diffusion"; }
        if (p.startsWith("parametres")) { return "Paramètres"; }
        if (p.startsWith("wa-web") || p.startsWith("whatsapp")) { return "WhatsApp"; }
        if (p.startsWith("orders") || p.startsWith("opportunities")) { return "CRM"; }
        return "l'application";
    }

    /** Remonte la chaîne des causes pour retrouver une exception du type voulu (déballe EJBException). */
    @SuppressWarnings("unchecked")
    private <T extends Throwable> T chercher(Throwable ex, Class<T> type) {
        Throwable courant = ex;
        int garde = 0;
        while (courant != null && garde++ < 12) {
            if (type.isInstance(courant)) { return (T) courant; }
            courant = courant.getCause();
        }
        return null;
    }
}
