package com.project.guide.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.net.URLDecoder;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Fetches article summaries from the Wikipedia REST API.
 */
@Slf4j
@Service
public class WikipediaService {

    private static final String SUMMARY_API = "https://en.wikipedia.org/api/rest_v1/page/summary/";

    private final RestTemplate restTemplate;

    public WikipediaService(RestTemplateBuilder restTemplateBuilder, @Value("${api.user-agent}") String userAgent) {
        // Wikimedia requires a descriptive User-Agent
        this.restTemplate = restTemplateBuilder.defaultHeader(HttpHeaders.USER_AGENT, userAgent).build();
    }

    /**
     * @param wikipediaUrl article URL, e.g. https://en.wikipedia.org/wiki/Machu_Picchu
     * @return summary text, or null if there is no URL or the request failed
     */
    public String fetchWikipediaExtract(String wikipediaUrl) {
        if (wikipediaUrl == null || wikipediaUrl.isEmpty()) {
            return null;
        }

        // The title may already be URL-encoded: decode it, then encode exactly once.
        // URI instead of String, so RestTemplate does not encode it again.
        String title = URLDecoder.decode(wikipediaUrl.substring(wikipediaUrl.lastIndexOf('/') + 1), UTF_8);
        URI uri = URI.create(SUMMARY_API + UriUtils.encode(title, UTF_8));

        try {
            Summary summary = restTemplate.getForObject(uri, Summary.class);
            return summary == null ? null : summary.extract();
        } catch (RestClientException e) {
            log.warn("Failed to fetch Wikipedia summary for {}: {}", wikipediaUrl, e.getMessage());
            return null;
        }
    }

    record Summary(String extract) {
    }
}
