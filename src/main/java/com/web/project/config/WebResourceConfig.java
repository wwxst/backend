package com.web.project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 暴露应用上传的静态资源。
 */
@Configuration
public class WebResourceConfig implements WebMvcConfigurer {

    private final String uploadLocation;

    public WebResourceConfig(
            @Value("${app.file.upload-dir:uploads}") String uploadDirectory
    ) {
        uploadLocation = Paths.get(uploadDirectory).toAbsolutePath().normalize().toUri().toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadLocation);
    }
}
