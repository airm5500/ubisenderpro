package com.ubisenderpro.rest;

import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.NotificationService;

import javax.ejb.EJB;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;

/**
 * Centre de notifications : agrégat par type (éléments récents + discussions non lues).
 */
@Path("/notifications")
@Secured
@Produces(MediaType.APPLICATION_JSON)
public class NotificationResource {

    @EJB
    private NotificationService notificationService;

    @GET
    public Map<String, Object> resume() {
        return notificationService.resume();
    }
}
