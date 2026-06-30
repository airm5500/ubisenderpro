package com.ubisenderpro.rest;

import com.ubisenderpro.dto.HistoriqueLigne;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.HistoriqueService;

import javax.ejb.EJB;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * Historique global des envois, tous canaux confondus (discussions,
 * campagnes, envois de masse WhatsApp Web).
 */
@Path("/historique")
@Secured(menu = "historique")
@Produces(MediaType.APPLICATION_JSON)
public class HistoriqueResource {

    @EJB
    private HistoriqueService service;

    @GET
    public List<HistoriqueLigne> lister(@QueryParam("canal") String canal,
                                        @QueryParam("type") String type,
                                        @QueryParam("q") String q,
                                        @QueryParam("dateDebut") String dateDebut,
                                        @QueryParam("dateFin") String dateFin,
                                        @QueryParam("limit") Integer limit) {
        return service.lister(canal, type, q, dateDebut, dateFin, limit == null ? 200 : limit);
    }
}
