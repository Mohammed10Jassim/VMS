package com.rkt.VisitorManagementSystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebStaticResourcesConfig implements WebMvcConfigurer {

    @Value("${app.storage.root:}")
    private String storageRoot;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (StringUtils.hasText(storageRoot)) {
            Path rootPath = Paths.get(storageRoot).toAbsolutePath().normalize();
            String location = rootPath.toUri().toString(); // e.g. file:/.../storage/
            registry.addResourceHandler("/files/**")
                    .addResourceLocations(location)
                    .setCachePeriod(3600);
        }
    }
}
