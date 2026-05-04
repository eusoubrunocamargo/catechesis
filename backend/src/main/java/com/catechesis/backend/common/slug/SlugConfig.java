package com.catechesis.backend.common.slug;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlugConfig {

    @Bean
    public SlugGenerator slugGenerator() {
        return new SlugGenerator();
    }
}