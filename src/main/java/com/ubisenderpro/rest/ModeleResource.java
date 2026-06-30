package com.ubisenderpro.rest;

import com.ubisenderpro.entity.ModeleMessage;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.AuditService;
import com.ubisenderpro.service.ModeleDocxService;
import com.ubisenderpro.service.ModeleService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Path("/templates")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ModeleResource {

    @EJB
    private ModeleService modeleService;
    @EJB
    private AuditService auditService;
    @EJB
    private ModeleDocxService docxService;

    @GET
    public List<ModeleMessage> lister() { return modeleService.lister(); }

    @GET
    @Path("/{id}")
    public Response parId(@PathParam("id") Long id) {
        return modeleService.parId(id).map(m -> Response.ok(m).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Secured(menu = "marketing")
    public Response creer(ModeleMessage m, @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        ModeleMessage cree = modeleService.creer(m);
        auditService.tracer(auth, "CREATION", "Modele", cree.getId(), cree.getNom());
        return Response.status(Response.Status.CREATED).entity(cree).build();
    }

    @PUT
    @Path("/{id}")
    @Secured(menu = "marketing")
    public Response modifier(@PathParam("id") Long id, ModeleMessage m,
                             @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        m.setId(id);
        ModeleMessage mod = modeleService.modifier(m);
        auditService.tracer(auth, "MODIFICATION", "Modele", id, mod.getNom());
        return Response.ok(mod).build();
    }

    @DELETE
    @Path("/{id}")
    @Secured(menu = "marketing")
    public Response supprimer(@PathParam("id") Long id,
                              @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        modeleService.supprimer(id);
        auditService.tracer(auth, "SUPPRESSION", "Modele", id, null);
        return Response.noContent().build();
    }

    /** Export d'un modèle au format Word (.docx) : renvoie le nom de fichier + contenu base64. */
    @GET
    @Path("/{id}/docx")
    public Response exporterDocx(@PathParam("id") Long id) throws Exception {
        ModeleMessage m = modeleService.parId(id).orElse(null);
        if (m == null) { return Response.status(Response.Status.NOT_FOUND).build(); }
        byte[] docx = docxService.exporter(m);
        return Response.ok(Map.of(
                "nomFichier", docxService.nomFichier(m),
                "mime", ModeleDocxService.MIME,
                "base64", Base64.getEncoder().encodeToString(docx))).build();
    }

    /** Import d'un modèle depuis un .docx exporté (contenu base64). Crée un nouveau modèle. */
    @POST
    @Path("/import-docx")
    @Secured(menu = "marketing")
    public Response importerDocx(Map<String, Object> body,
                                 @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) throws Exception {
        String b64 = body == null ? null : (String) body.get("fichierBase64");
        if (b64 == null || b64.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erreur", "Fichier .docx manquant.")).build();
        }
        byte[] contenu = Base64.getDecoder().decode(b64);
        ModeleMessage m = docxService.importer(contenu);
        ModeleMessage cree = modeleService.creer(m);
        auditService.tracer(auth, "IMPORT", "Modele", cree.getId(), cree.getNom());
        return Response.status(Response.Status.CREATED).entity(cree).build();
    }
}
