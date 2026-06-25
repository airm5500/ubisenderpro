package com.ubisenderpro.rest;

import com.ubisenderpro.entity.DispoEvenement;
import com.ubisenderpro.entity.DispoProduit;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.DispoEvenementService;
import com.ubisenderpro.service.DispoProduitService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Path("/dispo-evenements")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DispoEvenementResource {

    @EJB
    private DispoEvenementService service;
    @EJB
    private DispoProduitService produitService;

    @GET
    public List<DispoEvenement> lister(@QueryParam("type") String type,
                                       @QueryParam("statut") String statut,
                                       @QueryParam("historique") boolean historique) {
        if (historique) { return service.listerHistorique(); }
        if (statut != null && !statut.isEmpty()) { return service.listerParStatut(statut); }
        if (type != null && !type.isEmpty()) { return service.listerParType(type); }
        return service.lister();
    }

    @GET
    @Path("/{id}")
    public Response parId(@PathParam("id") Long id) {
        return service.parId(id).map(e -> Response.ok(e).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Secured(roles = {"ADMIN", "MARKETING", "CATALOGUE"})
    public Response creer(DispoEvenement e) {
        return Response.status(Response.Status.CREATED).entity(service.creer(e)).build();
    }

    @PUT
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "MARKETING", "CATALOGUE"})
    public Response modifier(@PathParam("id") Long id, DispoEvenement e) {
        e.setId(id);
        return Response.ok(service.modifier(e)).build();
    }

    @DELETE
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "MARKETING", "CATALOGUE"})
    public Response supprimer(@PathParam("id") Long id) {
        service.supprimer(id);
        return Response.noContent().build();
    }

    /* ---------- Actions ---------- */

    @POST
    @Path("/{id}/dupliquer")
    @Secured(roles = {"ADMIN", "MARKETING", "CATALOGUE"})
    public Response dupliquer(@PathParam("id") Long id) {
        DispoEvenement c = service.dupliquer(id);
        return c == null ? Response.status(Response.Status.NOT_FOUND).build()
                : Response.status(Response.Status.CREATED).entity(c).build();
    }

    @POST
    @Path("/{id}/programmer")
    @Secured(roles = {"ADMIN", "MARKETING", "CATALOGUE"})
    public Response programmer(@PathParam("id") Long id) {
        DispoEvenement e = service.programmer(id);
        return e == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(e).build();
    }

    @POST
    @Path("/{id}/annuler")
    @Secured(roles = {"ADMIN", "MARKETING", "CATALOGUE"})
    public Response annuler(@PathParam("id") Long id) {
        DispoEvenement e = service.annuler(id);
        return e == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(e).build();
    }

    @POST
    @Path("/{id}/archiver")
    @Secured(roles = {"ADMIN", "MARKETING", "CATALOGUE"})
    public Response archiver(@PathParam("id") Long id) {
        DispoEvenement e = service.archiver(id);
        return e == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(e).build();
    }

    /* ---------- Produits d'un événement ---------- */

    @GET
    @Path("/{id}/produits")
    public List<DispoProduit> produits(@PathParam("id") Long id) {
        return produitService.lister(id);
    }

    @POST
    @Path("/{id}/produits")
    @Secured(roles = {"ADMIN", "MARKETING", "CATALOGUE"})
    public Response ajouterProduit(@PathParam("id") Long id, DispoProduit p) {
        return Response.status(Response.Status.CREATED).entity(produitService.creer(id, p)).build();
    }

    @PUT
    @Path("/{id}/produits/{pid}")
    @Secured(roles = {"ADMIN", "MARKETING", "CATALOGUE"})
    public Response modifierProduit(@PathParam("pid") Long pid, DispoProduit p) {
        DispoProduit r = produitService.modifier(pid, p);
        return r == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(r).build();
    }

    @DELETE
    @Path("/{id}/produits/{pid}")
    @Secured(roles = {"ADMIN", "MARKETING", "CATALOGUE"})
    public Response supprimerProduit(@PathParam("pid") Long pid) {
        produitService.supprimer(pid);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/produits/import")
    @Secured(roles = {"ADMIN", "MARKETING", "CATALOGUE"})
    public Response importerProduits(@PathParam("id") Long id, Map<String, Object> body) throws Exception {
        String b64 = body == null ? null : (String) body.get("fichierBase64");
        if (b64 == null || b64.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erreur", "Fichier Excel manquant.")).build();
        }
        return Response.ok(produitService.importer(id, Base64.getDecoder().decode(b64))).build();
    }
}
