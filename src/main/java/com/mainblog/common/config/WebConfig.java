package com.mainblog.common.config;

import com.mainblog.common.storage.LocalDiskFileStorage;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 저장한 파일을 /files/{저장 이름}으로 내보낸다(D-75). */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LocalDiskFileStorage storage;

    public WebConfig(LocalDiskFileStorage storage) {
        this.storage = storage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(LocalDiskFileStorage.URL_PREFIX + "**")
                .addResourceLocations(storage.root().toUri().toString() + "/");
    }
}
