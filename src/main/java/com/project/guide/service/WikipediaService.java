package com.project.guide.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
public class WikipediaService {

    private final RestTemplate restTemplate;

    @Value("${api.user-agent}")
    private String userAgent;

    public WikipediaService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    /**
     * Fetch Wikipedia extract (summary) from Wikipedia REST API
     * @param wikipediaUrl Full Wikipedia URL (e.g., https://en.wikipedia.org/wiki/Machu_Picchu)
     * @return Extract text or null if not found
     */
    public String fetchWikipediaExtract(String wikipediaUrl) {
        if (wikipediaUrl == null || wikipediaUrl.isEmpty()) {
            return null;
        }

        try {
            // Extract article title from URL
            // https://en.wikipedia.org/wiki/Iguaçu_National_Park -> Iguaçu_National_Park
            String articleTitle = wikipediaUrl.substring(wikipediaUrl.lastIndexOf("/") + 1);

            // URL decode first (in case it's already encoded in the URL)
            articleTitle = java.net.URLDecoder.decode(articleTitle, StandardCharsets.UTF_8);

            // Then encode properly for the API
            String encodedTitle = UriUtils.encode(articleTitle, StandardCharsets.UTF_8);

            // Wikipedia REST API endpoint
            String apiUrl = String.format(
                    "https://en.wikipedia.org/api/rest_v1/page/summary/%s",
                    encodedTitle
            );

            log.info("Fetching Wikipedia extract from: {}", apiUrl);

            // Create headers with User-Agent (REQUIRED by Wikipedia)
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", userAgent);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Make HTTP request with headers
            // URI instead of String: the title is already encoded and must not be encoded again
            ResponseEntity<Map> response = restTemplate.exchange(
                    URI.create(apiUrl),
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            Map<String, Object> responseBody = response.getBody();

            if (responseBody != null && responseBody.containsKey("extract")) {
                return (String) responseBody.get("extract");
            }

            return null;

        } catch (Exception e) {
            log.error("Error fetching Wikipedia extract for URL: {}", wikipediaUrl, e);
            return null;
        }
    }
}
