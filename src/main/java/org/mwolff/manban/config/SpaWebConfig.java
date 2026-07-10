package org.mwolff.manban.config;

import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * SPA-Fallback: Das gebaute Frontend (React, Client-seitiges Routing) liegt unter
 * {@code classpath:/static/}. Direkte Aufrufe/Reloads von Client-Routen wie
 * {@code /admin/bootstrap} oder {@code /boards/1} treffen sonst kein Backend-Mapping
 * und liefern 404. Existiert die angefragte Ressource nicht und ist es kein
 * {@code /api}-Pfad, wird {@code index.html} ausgeliefert — React Router übernimmt dann.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // API-Pfade nie auf die SPA umleiten (bleiben 401/404 über die Controller/Filter).
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
