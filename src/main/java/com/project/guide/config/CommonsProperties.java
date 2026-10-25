package com.project.guide.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Wikimedia Commons settings from commons.yml.
 *
 * @param nonPhotoWords files with one of these words in the name are not photos of the place
 */
@ConfigurationProperties(prefix = "commons")
public record CommonsProperties(
        String apiEndpoint,
        int batchSize,
        List<String> nonPhotoWords
) {
}
