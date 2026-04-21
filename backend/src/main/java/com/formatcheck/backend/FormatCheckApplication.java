package com.formatcheck.backend;

import com.formatcheck.backend.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class FormatCheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(FormatCheckApplication.class, args);
    }
}
