package com.ubisenderpro.config;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

/**
 * Point d'entrée JAX-RS. Toutes les ressources sont exposées sous /api/v1.
 */
@ApplicationPath("/api/v1")
public class RestApplication extends Application {
}
