package com.ubisenderpro.rest;

import com.ubisenderpro.service.RapportService;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

/**
 * Consultation d'un document fraîchement généré via un lien éphémère
 * {@code /rapports-vue/{jeton}/{nomFichier}} : l'onglet du navigateur affiche
 * ainsi le NOM DU FICHIER dans sa barre d'adresse (un blob mémoire montrait
 * « blob:http://…/uuid »).
 *
 * <p>Pas de {@code @Secured} : le jeton EST l'autorisation — émis par un
 * endpoint authentifié, à usage unique, valable deux minutes, impossible à
 * deviner (UUID) et jamais listé. Le nom de fichier de l'URL est purement
 * cosmétique : le contenu servi est celui du jeton, et le nom est contrôlé
 * pour éviter tout lien trompeur.</p>
 */
@Path("/rapports-vue")
public class RapportVueResource {

    @GET
    @Path("/{jeton}/{nomFichier}")
    public Response voir(@PathParam("jeton") String jeton,
                         @PathParam("nomFichier") String nomFichier) {
        Object[] t = RapportService.consommerTicket(jeton);
        if (t == null || !String.valueOf(t[1]).equals(nomFichier)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .type("text/html;charset=UTF-8")
                    .entity("<html><body style=\"font-family:sans-serif;color:#555\">"
                            + "<h3>Document expiré</h3><p>Ce lien de consultation n'est valable "
                            + "que quelques minutes. Relancez l'impression depuis l'application."
                            + "</p></body></html>")
                    .build();
        }
        String type = nomFichier.toLowerCase().endsWith(".xlsx")
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "application/pdf";
        return Response.ok((byte[]) t[0]).type(type)
                .header("Content-Disposition", "inline; filename=\"" + nomFichier + "\"")
                // Document à usage unique : le navigateur ne doit pas le remettre en cache.
                .header("Cache-Control", "no-store")
                .build();
    }
}
