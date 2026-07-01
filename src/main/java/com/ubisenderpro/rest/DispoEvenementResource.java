package com.ubisenderpro.rest;

import com.ubisenderpro.entity.DispoEvenement;
import com.ubisenderpro.entity.DispoProduit;
import com.ubisenderpro.security.AuthenticatedUser;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.security.SessionStore;
import com.ubisenderpro.service.DispoEvenementService;
import com.ubisenderpro.service.DispoProduitService;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
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
    @Inject
    private SessionStore sessionStore;

    private String createur(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) { return null; }
        AuthenticatedUser u = sessionStore.validate(authHeader.substring("Bearer ".length()).trim());
        if (u == null) { return null; }
        return u.getNomComplet() != null && !u.getNomComplet().isEmpty() ? u.getNomComplet() : u.getLogin();
    }

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
    @Secured(menu = "dispo")
    public Response creer(DispoEvenement e, @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        if (e.getCreePar() == null || e.getCreePar().isEmpty()) { e.setCreePar(createur(authHeader)); }
        return Response.status(Response.Status.CREATED).entity(service.creer(e)).build();
    }

    @PUT
    @Path("/{id}")
    @Secured(menu = "dispo")
    public Response modifier(@PathParam("id") Long id, DispoEvenement e) {
        e.setId(id);
        return Response.ok(service.modifier(e)).build();
    }

    @DELETE
    @Path("/{id}")
    @Secured(menu = "dispo")
    public Response supprimer(@PathParam("id") Long id) {
        service.supprimer(id);
        return Response.noContent().build();
    }

    /* ---------- Actions ---------- */

    @POST
    @Path("/{id}/dupliquer")
    @Secured(menu = "dispo")
    public Response dupliquer(@PathParam("id") Long id) {
        DispoEvenement c = service.dupliquer(id);
        return c == null ? Response.status(Response.Status.NOT_FOUND).build()
                : Response.status(Response.Status.CREATED).entity(c).build();
    }

    @POST
    @Path("/{id}/programmer")
    @Secured(menu = "dispo")
    public Response programmer(@PathParam("id") Long id) {
        DispoEvenement e = service.programmer(id);
        return e == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(e).build();
    }

    @POST
    @Path("/{id}/annuler")
    @Secured(menu = "dispo")
    public Response annuler(@PathParam("id") Long id) {
        DispoEvenement e = service.annuler(id);
        return e == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(e).build();
    }

    @POST
    @Path("/{id}/archiver")
    @Secured(menu = "dispo")
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
    @Secured(menu = "dispo")
    public Response ajouterProduit(@PathParam("id") Long id, DispoProduit p) {
        return Response.status(Response.Status.CREATED).entity(produitService.creer(id, p)).build();
    }

    @PUT
    @Path("/{id}/produits/{pid}")
    @Secured(menu = "dispo")
    public Response modifierProduit(@PathParam("pid") Long pid, DispoProduit p) {
        DispoProduit r = produitService.modifier(pid, p);
        return r == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(r).build();
    }

    @DELETE
    @Path("/{id}/produits/{pid}")
    @Secured(menu = "dispo")
    public Response supprimerProduit(@PathParam("pid") Long pid) {
        produitService.supprimer(pid);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/produits/import")
    @Secured(menu = "dispo")
    public Response importerProduits(@PathParam("id") Long id, Map<String, Object> body) throws Exception {
        String b64 = body == null ? null : (String) body.get("fichierBase64");
        if (b64 == null || b64.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erreur", "Fichier Excel manquant.")).build();
        }
        return Response.ok(produitService.importer(id, Base64.getDecoder().decode(b64))).build();
    }
}
