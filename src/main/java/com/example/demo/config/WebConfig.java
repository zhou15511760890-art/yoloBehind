package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${storage.base-dir:files}")
    private String storageBaseDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String fileLocation = "file:" + storageBaseDir + "/";
        registry.addResourceHandler("/files/**")
                .addResourceLocations(fileLocation);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/error").setViewName("forward:/index.html");
    }
}
