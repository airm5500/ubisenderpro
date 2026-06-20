package com.ubisenderpro.rest;

import com.ubisenderpro.entity.SegmentationClient;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.SegmentationService;

import javax.ejb.EJB;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/segmentations")
@Secured
@Produces(MediaType.APPLICATION_JSON)
public class SegmentationResource {

    @EJB
    private SegmentationService segmentationService;

    @GET
    public List<SegmentationClient> lister() {
        return segmentationService.lister();
    }
}
