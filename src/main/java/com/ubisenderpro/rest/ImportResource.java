package com.ubisenderpro.rest;

import com.ubisenderpro.dto.ImportClientRequest;
import com.ubisenderpro.dto.ImportReport;
import com.ubisenderpro.entity.ImportLog;
import com.ubisenderpro.entity.ImportMapping;
import com.ubisenderpro.security.AuthenticatedUser;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.security.SessionStore;
import com.ubisenderpro.service.ArticleImportService;
import com.ubisenderpro.service.ClientImportService;
import com.ubisenderpro.service.ImportMappingService;
import com.ubisenderpro.service.ImportQueryService;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/imports")
@Secured(roles = {"ADMIN", "MARKETING", "CATALOGUE"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ImportResource {

    @EJB
    private ClientImportService clientImportService;
    @EJB
    private ArticleImportService articleImportService;
    @EJB
    private ImportMappingService importMappingService;
    @EJB
    private ImportQueryService importQueryService;
    @Inject
    private SessionStore sessionStore;

    /**
     * Import de clients/contacts. Le fichier est transmis en base64 dans la requête
     * (assistant d'import — sections 10 et 25 de la spec).
     */
    @POST
    @Path("/clients")
    public Response importerClients(ImportClientRequest req,
                                    @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        Long utilisateurId = utilisateurId(authHeader);
        ImportReport rapport = clientImportService.importer(req, utilisateurId);
        return Response.ok(rapport).build();
    }

    /** Import de catalogue articles (sections 14 et 25 de la spec). */
    @POST
    @Path("/articles")
    public Response importerArticles(ImportClientRequest req,
                                     @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        Long utilisateurId = utilisateurId(authHeader);
        ImportReport rapport = articleImportService.importer(req, utilisateurId);
        return Response.ok(rapport).build();
    }

    /**
     * Détection des colonnes d'un fichier avant l'import : intitulés réels et
     * premières valeurs. Le CSV était analysé côté navigateur, ce qui laissait
     * l'utilisateur d'un classeur Excel saisir lui-même les noms de colonnes ;
     * l'analyse est ici faite par le serveur, qui sait lire les deux formats.
     */
    @POST
    @Path("/colonnes")
    public Response colonnes(java.util.Map<String, Object> body) {
        String base64 = texte(body.get("fichierBase64"));
        String nomFichier = texte(body.get("nomFichier"));
        String sep = texte(body.get("separateur"));
        if (base64 == null || base64.isEmpty()) {
            throw new com.ubisenderpro.service.ValidationException("fichier", "Aucun fichier fourni.");
        }
        char separateur = (sep == null || sep.isEmpty()) ? ';' : sep.charAt(0);
        try {
            byte[] contenu = java.util.Base64.getDecoder().decode(base64);
            return Response.ok(com.ubisenderpro.importer.FileParser.apercu(
                    contenu, nomFichier, separateur, 3)).build();
        } catch (IllegalArgumentException e) {
            throw new com.ubisenderpro.service.ValidationException("fichier", "Fichier illisible (encodage).");
        } catch (Exception e) {
            throw new com.ubisenderpro.service.ValidationException("fichier",
                    "Lecture du fichier impossible. Vérifiez qu'il s'agit bien d'un .csv ou d'un .xlsx"
                    + (com.ubisenderpro.importer.FileParser.estExcel(nomFichier)
                       ? " (les classeurs .xls anciens doivent être réenregistrés en .xlsx)." : "."));
        }
    }

    private static String texte(Object o) { return o == null ? null : String.valueOf(o).trim(); }

    // ----- Modèles de correspondance (mappings sauvegardés) -----

    @GET
    @Path("/mappings")
    public List<ImportMapping> listerMappings(@QueryParam("type") String typeImport) {
        return importMappingService.lister(typeImport);
    }

    @POST
    @Path("/mappings")
    public Response enregistrerMapping(ImportMapping m) {
        return Response.status(Response.Status.CREATED).entity(importMappingService.enregistrer(m)).build();
    }

    @DELETE
    @Path("/mappings/{id}")
    public Response supprimerMapping(@PathParam("id") Long id) {
        importMappingService.supprimer(id);
        return Response.noContent().build();
    }

    // ----- Journal d'import et export des rejets -----

    @GET
    public List<ImportLog> listerImports(@QueryParam("start") @DefaultValue("0") int start,
                                         @QueryParam("limit") @DefaultValue("25") int limit) {
        return importQueryService.lister(start, limit);
    }

    @GET
    @Path("/{id}")
    public Response detailImport(@PathParam("id") Long id) {
        return importQueryService.parId(id).map(i -> Response.ok(i).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /** Télécharge les lignes rejetées/ignorées au format CSV (section 25.1 étape 13). */
    @GET
    @Path("/{id}/errors")
    @Produces("text/csv")
    public Response telechargerRejets(@PathParam("id") Long id) {
        String csv = importQueryService.csvRejets(id);
        return Response.ok(csv)
                .header("Content-Disposition", "attachment; filename=\"import_" + id + "_rejets.csv\"")
                .build();
    }

    private Long utilisateurId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        AuthenticatedUser u = sessionStore.validate(authHeader.substring("Bearer ".length()).trim());
        return u == null ? null : u.getId();
    }
}
