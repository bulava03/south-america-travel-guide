package com.project.guide.service;

import com.project.guide.dto.WikidataAttraction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Fetches descriptions of attractions from English Wikipedia during the sync:
 * the first paragraph of the article, as plain text.
 */
@Slf4j
@Service
public class WikipediaService {

    static final String API = "https://en.wikipedia.org/w/api.php";
    /** API limit of intro extracts per request. */
    static final int BATCH_SIZE = 20;
    private static final String ARTICLE_PATH = "/wiki/";

    private final RestTemplate restTemplate;

    public WikipediaService(RestTemplateBuilder restTemplateBuilder, @Value("${api.user-agent}") String userAgent) {
        // Wikimedia requires a descriptive User-Agent
        this.restTemplate = restTemplateBuilder.defaultHeader(HttpHeaders.USER_AGENT, userAgent).build();
    }

    /**
     * Sets the description of every attraction with a Wikipedia article.
     * If a request fails, descriptions of that batch stay null and the sync goes on.
     */
    public void addDescriptions(List<WikidataAttraction> attractions) {
        // Article title -> attractions; the same article is requested once
        Map<String, List<WikidataAttraction>> byTitle = new LinkedHashMap<>();
        for (WikidataAttraction attraction : attractions) {
            String title = title(attraction.getWikipediaUrl());
            if (title != null) {
                byTitle.computeIfAbsent(title, t -> new ArrayList<>()).add(attraction);
            }
        }

        List<String> titles = new ArrayList<>(byTitle.keySet());
        int failed = 0;
        for (int from = 0; from < titles.size(); from += BATCH_SIZE) {
            List<String> batch = titles.subList(from, Math.min(from + BATCH_SIZE, titles.size()));
            try {
                fetchExtracts(batch).forEach((title, extract) ->
                        byTitle.get(title).forEach(attraction -> attraction.setDescription(extract)));
            } catch (RuntimeException e) {
                failed += batch.size();
                log.warn("Failed to fetch Wikipedia descriptions of {}: {}", batch, e.getMessage());
            }
        }
        log.info("Wikipedia descriptions: {} articles, {} not fetched", titles.size(), failed);
    }

    /**
     * @param titles article titles as in the URL, e.g. "Machu_Picchu"
     * @return first paragraph by the given title; articles without text are left out
     */
    Map<String, String> fetchExtracts(List<String> titles) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("action", "query");
        form.add("prop", "extracts");
        form.add("exintro", "1");
        form.add("explaintext", "1");
        form.add("exlimit", "max");
        form.add("redirects", "1");
        form.add("format", "json");
        form.add("formatversion", "2");
        form.add("titles", String.join("|", titles));

        Response response = restTemplate.postForObject(API, form, Response.class);
        if (response == null || response.query() == null) {
            throw new IllegalStateException("Unexpected Wikipedia response: " + response);
        }

        // The API normalizes titles ("_" -> " ") and follows redirects, then returns pages by their final title
        Map<String, String> renamed = new HashMap<>();
        Objects.requireNonNullElse(response.query().normalized(), List.<Response.Rename>of())
                .forEach(rename -> renamed.put(rename.from(), rename.to()));
        Objects.requireNonNullElse(response.query().redirects(), List.<Response.Rename>of())
                .forEach(rename -> renamed.put(rename.from(), rename.to()));
        Map<String, String> extracts = new HashMap<>();
        Objects.requireNonNullElse(response.query().pages(), List.<Response.Page>of())
                .forEach(page -> extracts.put(page.title(), firstParagraph(page.extract())));

        Map<String, String> result = new HashMap<>();
        for (String title : titles) {
            String resolved = title;
            // At most normalized -> redirect target
            for (int i = 0; i < 2 && renamed.containsKey(resolved); i++) {
                resolved = renamed.get(resolved);
            }
            String extract = extracts.get(resolved);
            if (extract != null) {
                result.put(title, extract);
            }
        }
        return result;
    }

    /**
     * "https://en.wikipedia.org/wiki/Igua%C3%A7u_National_Park" -> "Iguaçu_National_Park";
     * null if there is no article.
     */
    static String title(String wikipediaUrl) {
        if (wikipediaUrl == null || !wikipediaUrl.contains(ARTICLE_PATH)) {
            return null;
        }
        // Titles may contain "/", so everything after /wiki/ is the title
        String title = wikipediaUrl.substring(wikipediaUrl.indexOf(ARTICLE_PATH) + ARTICLE_PATH.length());
        return title.isEmpty() ? null : URLDecoder.decode(title, UTF_8);
    }

    /**
     * The intro of an article may have several paragraphs; the page shows the first one.
     */
    static String firstParagraph(String extract) {
        if (extract == null) {
            return null;
        }
        return extract.lines().map(String::strip).filter(line -> !line.isEmpty()).findFirst().orElse(null);
    }

    record Response(Query query) {
        record Query(List<Rename> normalized, List<Rename> redirects, List<Page> pages) {
        }

        record Rename(String from, String to) {
        }

        record Page(String title, String extract) {
        }
    }
}
