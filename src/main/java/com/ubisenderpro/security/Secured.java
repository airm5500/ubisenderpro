package com.ubisenderpro.security;

import javax.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marque une ressource ou une méthode comme nécessitant une session valide.
 * Optionnellement, restreint l'accès à certains rôles.
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Secured {
    String[] roles() default {};

    /** Menu requis (RBAC fin). Vide = pas de contrôle par permission. */
    String menu() default "";

    /** Action requise sur le menu (VOIR, CREER, MODIFIER, SUPPRIMER, DESACTIVER…). */
    String action() default "";
}
