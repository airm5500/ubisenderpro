package com.ubisenderpro.rest;

import com.ubisenderpro.dto.PageResult;
import com.ubisenderpro.entity.Article;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.ArticleService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.Map;

@Path("/articles")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ArticleResource {

    @EJB
    private ArticleService articleService;

    @GET
    public PageResult<Article> lister(@QueryParam("q") String q,
                                      @QueryParam("categorieId") Long categorieId,
                                      @QueryParam("marqueId") Long marqueId,
                                      @QueryParam("actif") Boolean actif,
                                      @QueryParam("start") @DefaultValue("0") int start,
                                      @QueryParam("limit") @DefaultValue("25") int limit) {
        return articleService.rechercher(q, categorieId, marqueId, actif, start, limit);
    }

    @GET
    @Path("/{id}")
    public Response parId(@PathParam("id") Long id) {
        return articleService.parId(id).map(a -> Response.ok(a).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response creer(Article a) {
        return Response.status(Response.Status.CREATED).entity(articleService.creer(a)).build();
    }

    @PUT
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response modifier(@PathParam("id") Long id, Article a) {
        if (!articleService.parId(id).isPresent()) return Response.status(Response.Status.NOT_FOUND).build();
        a.setId(id);
        return Response.ok(articleService.modifier(a)).build();
    }

    @POST
    @Path("/{id}/stock")
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response ajusterStock(@PathParam("id") Long id, Map<String, Object> body) {
        BigDecimal qte = new BigDecimal(String.valueOf(body.getOrDefault("quantite", "0")));
        String type = (String) body.getOrDefault("type", "AJUSTEMENT_POSITIF");
        String commentaire = (String) body.get("commentaire");
        Article a = articleService.ajusterStock(id, qte, type, commentaire, null);
        return a == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(a).build();
    }

    @DELETE
    @Path("/{id}")
    @Secured(roles = {"ADMIN"})
    public Response supprimer(@PathParam("id") Long id) {
        articleService.supprimer(id);
        return Response.noContent().build();
    }

    /** Articles concernés par un code promo (aperçu avant mise à jour). */
    @GET
    @Path("/promo")
    public java.util.List<Article> articlesPromo(@QueryParam("code") String code) {
        return articleService.parCodePromo(code);
    }

    /** Mise à jour sélective des dates d'une promotion (tous les articles du code promo). */
    @POST
    @Path("/promo")
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response majPromo(Map<String, Object> body) {
        try {
            String code = body == null ? null : (String) body.get("codePromo");
            java.time.LocalDateTime debut = parseDate(body == null ? null : (String) body.get("dateDebut"), false);
            java.time.LocalDateTime fin = parseDate(body == null ? null : (String) body.get("dateFin"), true);
            int n = articleService.majDatesPromo(code, debut, fin);
            return Response.ok(Map.of("misAJour", n)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erreur", e.getMessage())).build();
        }
    }

    /** "yyyy-MM-dd" -> début (00:00) ou fin (23:59) de journée ; null si vide. */
    private java.time.LocalDateTime parseDate(String s, boolean finJournee) {
        if (s == null || s.trim().isEmpty()) { return null; }
        java.time.LocalDate d = java.time.LocalDate.parse(s.trim().substring(0, 10));
        return finJournee ? d.atTime(23, 59, 59) : d.atStartOfDay();
    }
}
