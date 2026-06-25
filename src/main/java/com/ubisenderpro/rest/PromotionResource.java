package com.ubisenderpro.rest;

import com.ubisenderpro.entity.Promotion;
import com.ubisenderpro.entity.PromotionProduit;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.PromotionProduitService;
import com.ubisenderpro.service.PromotionService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Path("/promotions")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PromotionResource {

    @EJB
    private PromotionService promotionService;
    @EJB
    private PromotionProduitService produitService;

    @GET
    public List<Promotion> lister(@QueryParam("statut") String statut) {
        return promotionService.listerParStatut(statut);
    }

    @GET
    @Path("/{id}")
    public Response parId(@PathParam("id") Long id) {
        return promotionService.parId(id).map(p -> Response.ok(p).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response creer(Promotion p) {
        try {
            return Response.status(Response.Status.CREATED).entity(promotionService.creer(p)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erreur", e.getMessage())).build();
        }
    }

    @PUT
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response modifier(@PathParam("id") Long id, Promotion p) {
        try {
            p.setId(id);
            return Response.ok(promotionService.modifier(p)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erreur", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response supprimer(@PathParam("id") Long id) {
        promotionService.supprimer(id);
        return Response.noContent().build();
    }

    /* ---------- Actions sur une promotion ---------- */

    @POST
    @Path("/{id}/dupliquer")
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response dupliquer(@PathParam("id") Long id) {
        Promotion c = promotionService.dupliquer(id);
        return c == null ? Response.status(Response.Status.NOT_FOUND).build()
                : Response.status(Response.Status.CREATED).entity(c).build();
    }

    @POST
    @Path("/{id}/annuler")
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response annuler(@PathParam("id") Long id) {
        Promotion p = promotionService.annuler(id);
        return p == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(p).build();
    }

    @POST
    @Path("/{id}/archiver")
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response archiver(@PathParam("id") Long id) {
        Promotion p = promotionService.archiver(id);
        return p == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(p).build();
    }

    /* ---------- Produits d'une promotion ---------- */

    @GET
    @Path("/{id}/produits")
    public List<PromotionProduit> produits(@PathParam("id") Long id) {
        return produitService.lister(id);
    }

    @POST
    @Path("/{id}/produits")
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response ajouterProduit(@PathParam("id") Long id, PromotionProduit p) {
        return Response.status(Response.Status.CREATED).entity(produitService.creer(id, p)).build();
    }

    @PUT
    @Path("/{id}/produits/{pid}")
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response modifierProduit(@PathParam("pid") Long pid, PromotionProduit p) {
        PromotionProduit r = produitService.modifier(pid, p);
        return r == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(r).build();
    }

    @DELETE
    @Path("/{id}/produits/{pid}")
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response supprimerProduit(@PathParam("pid") Long pid) {
        produitService.supprimer(pid);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/produits/import")
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response importerProduits(@PathParam("id") Long id, Map<String, Object> body) throws Exception {
        String b64 = body == null ? null : (String) body.get("fichierBase64");
        if (b64 == null || b64.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erreur", "Fichier Excel manquant.")).build();
        }
        return Response.ok(produitService.importer(id, Base64.getDecoder().decode(b64))).build();
    }
}
