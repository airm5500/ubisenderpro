package ci.ubipharm.crm.socle.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Sert l'interface Angular embarquee dans le jar (classpath:/static, copiee depuis front/dist au build).
 * Une route Angular (/factures, /campagnes/12...) n'est pas un fichier : elle renvoie index.html et le
 * routeur du navigateur prend le relais. Les routes /api ne sont jamais concernees.
 */
@Configuration
public class FrontConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String chemin, Resource emplacement) throws IOException {
                        Resource fichier = emplacement.createRelative(chemin);
                        if (fichier.exists() && fichier.isReadable()) {
                            return fichier;
                        }
                        if (chemin.startsWith("api/") || chemin.contains(".")) {
                            return null;
                        }
                        Resource index = emplacement.createRelative("index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
