package com.project.guide;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Main Spring Boot Application class.
 * South America Tourist Guide - Cultural and Natural Heritage.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SouthAmericaGuideApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(SouthAmericaGuideApplication.class, args);
    }
}
