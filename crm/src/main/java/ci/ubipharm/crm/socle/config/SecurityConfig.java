package ci.ubipharm.crm.socle.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import ci.ubipharm.crm.rest.service.impl.ControleSessionFiltre;
import jakarta.persistence.EntityManager;

/**
 * Session serveur (cookie HttpOnly) + protection CSRF adaptee a une application Angular :
 * le jeton est lu dans le cookie XSRF-TOKEN et renvoye dans l'en-tete X-XSRF-TOKEN.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filtres(HttpSecurity http, EntityManager em) throws Exception {
        http
            .addFilterBefore(new ControleSessionFiltre(em), AuthorizationFilter.class)
            // Les callbacks du service WhatsApp et des fournisseurs SMS n'ont ni session ni jeton CSRF : ils portent un jeton
            .csrf(csrf -> csrf.spa().ignoringRequestMatchers("/api/passerelle/**"))
            .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository()))
            .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout
                .logoutUrl("/api/auth/deconnexion")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                .deleteCookies("CRMSESSION"))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/connexion").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/passerelle/wa-web/**").permitAll()
                .requestMatchers("/api/passerelle/sms/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider fournisseur = new DaoAuthenticationProvider(userDetailsService);
        fournisseur.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(fournisseur);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
