package com.ubisenderpro.rest;

import com.ubisenderpro.entity.Parametre;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.ParametreService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Paramètres globaux (clé/valeur). Lecture ouverte à toute session ;
 * écriture réservée aux ADMIN.
 */
@Path("/parametres")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ParametreResource {

    @EJB
    private ParametreService service;

    @GET
    @Secured(roles = {"ADMIN"})
    public List<Parametre> lister() { return service.lister(); }

    @GET
    @Path("/{cle}")
    public Response parCle(@PathParam("cle") String cle) {
        Map<String, Object> r = new HashMap<>();
        r.put("cle", cle);
        r.put("valeur", service.valeur(cle, null));
        return Response.ok(r).build();
    }

    @PUT
    @Path("/{cle}")
    @Secured(roles = {"ADMIN"})
    public Response definir(@PathParam("cle") String cle, Map<String, Object> body) {
        String valeur = body == null || body.get("valeur") == null ? null : String.valueOf(body.get("valeur"));
        String desc = body != null && body.get("description") != null ? String.valueOf(body.get("description")) : null;
        String cat = body != null && body.get("categorie") != null ? String.valueOf(body.get("categorie")) : null;
        return Response.ok(service.definir(cle, valeur, desc, cat)).build();
    }
}
