package com.ubisenderpro.rest;

import com.ubisenderpro.dto.MediaUploadRequest;
import com.ubisenderpro.entity.MediaFichier;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.MediaFichierService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestion des fichiers média hébergés par l'application.
 *
 * - POST /api/v1/media/upload : téléverse un fichier (base64) et renvoie son URL publique.
 *   Cette URL peut être utilisée comme média d'en-tête de modèle ou comme média par lien.
 * - GET /api/v1/media/{id} : sert le fichier (NON sécurisé) afin que WhatsApp (et le
 *   navigateur, pour l'aperçu) puisse le récupérer par lien.
 */
@Path("/media")
public class MediaResource {

    @EJB
    private MediaFichierService mediaFichierService;

    @POST
    @Path("/upload")
    @Secured(roles = {"ADMIN", "MARKETING", "SUPERVISEUR", "AGENT"})
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response upload(MediaUploadRequest req, @Context UriInfo uriInfo) {
        if (req == null || req.getFichierBase64() == null || req.getFichierBase64().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(erreur("Fichier manquant")).build();
        }
        byte[] contenu;
        try {
            contenu = Base64.getDecoder().decode(req.getFichierBase64());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(erreur("Contenu base64 invalide")).build();
        }
        MediaFichier mf = mediaFichierService.enregistrer(contenu, req.getMimeType(), req.getNomFichier());

        URI url = uriInfo.getBaseUriBuilder().path("media").path(String.valueOf(mf.getId())).build();
        Map<String, Object> result = new HashMap<>();
        result.put("id", mf.getId());
        result.put("url", url.toString());
        result.put("mimeType", mf.getMimeType());
        result.put("nomFichier", mf.getNomFichier());
        result.put("taille", mf.getTaille());
        return Response.ok(result).build();
    }

    @GET
    @Path("/{id}")
    public Response telecharger(@PathParam("id") Long id) {
        return mediaFichierService.parId(id)
                .map(mf -> Response.ok(mf.getContenu())
                        .type(mf.getMimeType() == null || mf.getMimeType().isEmpty()
                                ? "application/octet-stream" : mf.getMimeType())
                        .header("Content-Disposition", "inline; filename=\""
                                + (mf.getNomFichier() == null ? ("media-" + id) : mf.getNomFichier()) + "\"")
                        .header("Cache-Control", "public, max-age=86400")
                        .build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    private Map<String, Object> erreur(String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("erreur", message);
        return m;
    }
}
