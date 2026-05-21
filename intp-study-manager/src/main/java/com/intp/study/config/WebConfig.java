package com.intp.study.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${app.data-dir:data}")
    private String dataDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Path.of(dataDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/data/**")
                .addResourceLocations(root.toUri().toString() + "/");
    }
}
