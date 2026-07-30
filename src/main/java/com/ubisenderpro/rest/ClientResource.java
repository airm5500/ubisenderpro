package com.ubisenderpro.rest;

import com.ubisenderpro.dto.PageResult;
import com.ubisenderpro.entity.Client;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.AuditService;
import com.ubisenderpro.service.ClientService;
import com.ubisenderpro.service.ContactService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Optional;

@Path("/clients")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClientResource {

    @EJB
    private ClientService clientService;
    @EJB
    private ContactService contactService;
    @EJB
    private AuditService auditService;

    @GET
    public PageResult<Client> lister(@QueryParam("q") String recherche,
                                     @QueryParam("agence") String agence,
                                     @QueryParam("region") String region,
                                     @QueryParam("commune") String commune,
                                     @QueryParam("tournee") String tournee,
                                     @QueryParam("segmentationId") Long segmentationId,
                                     @QueryParam("actif") Boolean actif,
                                     @QueryParam("start") @DefaultValue("0") int start,
                                     @QueryParam("limit") @DefaultValue("25") int limit) {
        return clientService.rechercher(recherche, agence, region, commune, tournee, segmentationId,
                actif, start, limit);
    }

    /** Valeurs distinctes pour alimenter les filtres (agences/régions/communes/tournées). */
    @GET
    @Path("/facettes")
    public java.util.Map<String, java.util.List<String>> facettes() {
        return clientService.facettes();
    }

    /** Sélecteur de clients (SCL) : clients actifs + contact principal, filtrables. */
    @GET
    @Path("/selection")
    public java.util.List<java.util.Map<String, Object>> selection(
            @QueryParam("q") String q,
            @QueryParam("agence") String agence,
            @QueryParam("region") String region,
            @QueryParam("segmentationId") Long segmentationId) {
        return clientService.pourSelectionClients(q, agence, region, segmentationId);
    }

    @GET
    @Path("/{id}")
    public Response parId(@PathParam("id") Long id) {
        Optional<Client> client = clientService.parId(id);
        return client.map(c -> Response.ok(c).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/{id}/contacts")
    public Response contacts(@PathParam("id") Long id,
                             @QueryParam("start") @DefaultValue("0") int start,
                             @QueryParam("limit") @DefaultValue("25") int limit) {
        return Response.ok(contactService.parClient(id, start, limit)).build();
    }

    @POST
    @Secured(menu = "clients")
    public Response creer(Client client, @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        Client c = clientService.creer(client);
        auditService.tracer(auth, "CREATION", "Client", c.getId(), c.getNomCompte());
        return Response.status(Response.Status.CREATED).entity(c).build();
    }

    @PUT
    @Path("/{id}")
    @Secured(menu = "clients")
    public Response modifier(@PathParam("id") Long id, Client client,
                             @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        if (!clientService.parId(id).isPresent()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        client.setId(id);
        Client c = clientService.modifier(client);
        auditService.tracer(auth, "MODIFICATION", "Client", id, c.getNomCompte());
        return Response.ok(c).build();
    }

    /**
     * Mise à jour sélective (onglet dédié) : déplace d'un coup les comptes
     * cochés vers une segmentation / agence / région / tournée. Seuls les
     * champs présents dans {@code champs} sont modifiés.
     */
    @POST
    @Path("/maj-selective")
    @Secured(menu = "clients")
    public Response majSelective(java.util.Map<String, Object> body,
                                 @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        Object brut = body == null ? null : body.get("ids");
        if (brut instanceof java.util.List) {
            for (Object o : (java.util.List<?>) brut) {
                if (o != null) { ids.add(Long.valueOf(String.valueOf(o))); }
            }
        }
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> champs = body == null ? null
                : (body.get("champs") instanceof java.util.Map
                    ? (java.util.Map<String, Object>) body.get("champs") : null);
        int n = clientService.majSelective(ids, champs);
        auditService.tracer(auth, "MODIFICATION", "Client", null,
                "Mise à jour sélective de " + n + " compte(s) : "
                + (champs == null ? "" : String.join(", ", champs.keySet())));
        java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("modifies", n);
        return Response.ok(r).build();
    }

    @DELETE
    @Path("/{id}")
    @Secured(roles = {"ADMIN"})
    public Response supprimer(@PathParam("id") Long id,
                              @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        clientService.supprimer(id);
        auditService.tracer(auth, "SUPPRESSION", "Client", id, null);
        return Response.noContent().build();
    }

    /** Écran « Contacts » (numéros seuls) : enregistre la liste des numéros du client. */
    @POST
    @Path("/{id}/numeros")
    @Secured(menu = "clients")
    public Response numeros(@PathParam("id") Long id,
                            java.util.List<java.util.Map<String, Object>> numeros,
                            @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        if (!clientService.parId(id).isPresent()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        clientService.enregistrerNumeros(id, numeros);
        auditService.tracer(auth, "MODIFICATION", "Client", id, "numéros");
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/activate")
    @Secured(menu = "clients")
    public Response activer(@PathParam("id") Long id, @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        Client c = clientService.definirActif(id, true);
        if (c == null) { return Response.status(Response.Status.NOT_FOUND).build(); }
        auditService.tracer(auth, "ACTIVATION", "Client", id, c.getNomCompte());
        return Response.ok(c).build();
    }

    @POST
    @Path("/{id}/deactivate")
    @Secured(menu = "clients")
    public Response desactiver(@PathParam("id") Long id, @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        Client c = clientService.definirActif(id, false);
        if (c == null) { return Response.status(Response.Status.NOT_FOUND).build(); }
        auditService.tracer(auth, "DESACTIVATION", "Client", id, c.getNomCompte());
        return Response.ok(c).build();
    }
}
