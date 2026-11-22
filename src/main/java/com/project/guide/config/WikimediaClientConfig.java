package com.project.guide.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * One HTTP client for the Wikidata search, Commons and Wikipedia APIs.
 * Wikimedia rejects requests with the default Java User-Agent (HTTP 403), so a descriptive one is sent.
 * Without timeouts a hung connection would block the sync, and a new one could not start until a restart.
 */
@Configuration
public class WikimediaClientConfig {

    @Bean
    public RestTemplate wikimediaRestTemplate(RestTemplateBuilder builder,
                                              @Value("${api.user-agent}") String userAgent,
                                              @Value("${api.connect-timeout}") Duration connectTimeout,
                                              @Value("${api.read-timeout}") Duration readTimeout) {
        return builder
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
    }
}
