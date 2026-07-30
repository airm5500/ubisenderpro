package com.ubisenderpro.rest;

import com.ubisenderpro.entity.ClientContact;
import com.ubisenderpro.entity.ListeDiffusion;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.ListeService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/lists")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ListeResource {

    @EJB
    private ListeService listeService;

    @GET
    public List<ListeDiffusion> lister() { return listeService.lister(); }

    @POST
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response creer(ListeDiffusion l) {
        return Response.status(Response.Status.CREATED).entity(listeService.creer(l)).build();
    }

    @PUT
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response modifier(@PathParam("id") Long id, ListeDiffusion l) {
        l.setId(id);
        return Response.ok(listeService.modifier(l)).build();
    }

    @GET
    @Path("/{id}/contacts")
    public List<ClientContact> contacts(@PathParam("id") Long id) {
        return listeService.contacts(id);
    }

    @POST
    @Path("/{id}/contacts")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response ajouterContact(@PathParam("id") Long id, Map<String, Object> body) {
        Long contactId = Long.valueOf(String.valueOf(body.get("contactId")));
        String source = body.get("source") == null ? "MANUEL" : String.valueOf(body.get("source"));
        listeService.ajouterContact(id, contactId, source);
        return Response.ok().build();
    }

    @DELETE
    @Path("/{id}/contacts/{contactId}")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response retirerContact(@PathParam("id") Long id, @PathParam("contactId") Long contactId) {
        listeService.retirerContact(id, contactId);
        return Response.noContent().build();
    }

    /** Retire tous les membres de la liste (la liste elle-même est conservée). */
    @DELETE
    @Path("/{id}/contacts")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response vider(@PathParam("id") Long id) {
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("retires", listeService.vider(id));
        return Response.ok(r).build();
    }

    /** Importe des clients dans la liste (un code client par ligne). */
    @POST
    @Path("/{id}/import-clients")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response importerClients(@PathParam("id") Long id, Map<String, Object> body) {
        String contenu = body == null ? null : (String) body.get("contenu");
        return Response.ok(listeService.importerClients(id, contenu)).build();
    }

    /**
     * Import assisté : l'écran a fait choisir, dans le fichier, la colonne qui
     * porte les codes clients ; le serveur extrait cette colonne (CSV ou Excel)
     * et ajoute le contact principal de chaque client. En simulation, rien
     * n'est écrit — le rapport sert à contrôler le fichier avant de l'appliquer.
     * À défaut de fichier, une liste de codes déjà extraits est acceptée
     * (codes collés au clavier).
     */
    @POST
    @Path("/{id}/import-codes")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response importerCodes(@PathParam("id") Long id, Map<String, Object> body) {
        boolean simulation = body != null && Boolean.parseBoolean(String.valueOf(body.get("simulation")));
        List<String> codes = new java.util.ArrayList<>();

        String base64 = body == null || body.get("fichierBase64") == null
                ? null : String.valueOf(body.get("fichierBase64"));
        if (base64 != null && !base64.isEmpty()) {
            String colonne = body.get("colonne") == null ? "" : String.valueOf(body.get("colonne"));
            if (colonne.trim().isEmpty()) {
                throw new com.ubisenderpro.service.ValidationException("colonne",
                        "Choisissez la colonne du fichier qui contient les codes clients.");
            }
            String nomFichier = body.get("nomFichier") == null ? "" : String.valueOf(body.get("nomFichier"));
            String sep = body.get("separateur") == null ? ";" : String.valueOf(body.get("separateur"));
            try {
                List<Map<String, String>> lignes = com.ubisenderpro.importer.FileParser.parse(
                        java.util.Base64.getDecoder().decode(base64), nomFichier,
                        sep.isEmpty() ? ';' : sep.charAt(0));
                for (Map<String, String> l : lignes) { codes.add(l.get(colonne)); }
            } catch (Exception e) {
                throw new com.ubisenderpro.service.ValidationException("fichier",
                        "Lecture du fichier impossible. Vérifiez qu'il s'agit bien d'un .csv ou d'un .xlsx.");
            }
        } else {
            Object brut = body == null ? null : body.get("codes");
            if (brut instanceof List) {
                for (Object o : (List<?>) brut) { codes.add(o == null ? "" : String.valueOf(o)); }
            }
        }
        return Response.ok(listeService.ajouterParCodes(id, codes, simulation)).build();
    }
}
