package com.project.guide.service;

import com.project.guide.config.CommonsProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Wikimedia Commons API: size and type of photo files.
 */
@Component
public class CommonsClient {

    private final RestTemplate restTemplate;
    private final CommonsProperties properties;

    public CommonsClient(RestTemplateBuilder restTemplateBuilder, CommonsProperties properties,
                         @Value("${api.user-agent}") String userAgent) {
        // Wikimedia rejects requests with the default Java User-Agent
        this.restTemplate = restTemplateBuilder.defaultHeader(HttpHeaders.USER_AGENT, userAgent).build();
        this.properties = properties;
    }

    /**
     * @param fileNames file names without the "File:" prefix, e.g. "Machu Picchu.jpg"
     * @return info by file name; files that do not exist on Commons are left out
     */
    public Map<String, ImageInfo> imageInfo(Collection<String> fileNames) {
        List<String> names = new ArrayList<>(new LinkedHashSet<>(fileNames));
        Map<String, ImageInfo> result = new HashMap<>();
        for (int from = 0; from < names.size(); from += properties.batchSize()) {
            result.putAll(fetchBatch(names.subList(from, Math.min(from + properties.batchSize(), names.size()))));
        }
        return result;
    }

    private Map<String, ImageInfo> fetchBatch(List<String> names) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("action", "query");
        form.add("prop", "imageinfo");
        form.add("iiprop", "size|mime");
        form.add("format", "json");
        form.add("titles", String.join("|", names.stream().map(name -> "File:" + name).toList()));

        Response response = restTemplate.postForObject(properties.apiEndpoint(), form, Response.class);
        if (response == null || response.query() == null) {
            throw new IllegalStateException("Unexpected Commons response: " + response);
        }

        // The API returns titles normalized ("_" -> " ", first letter upper case)
        Map<String, String> normalized = new HashMap<>();
        Optional.ofNullable(response.query().normalized())
                .ifPresent(list -> list.forEach(n -> normalized.put(n.from(), n.to())));
        Map<String, Response.Page> pages = new HashMap<>();
        response.query().pages().values().forEach(page -> pages.put(page.title(), page));

        Map<String, ImageInfo> result = new HashMap<>();
        for (String name : names) {
            String title = "File:" + name;
            Response.Page page = pages.get(normalized.getOrDefault(title, title));
            if (page != null && page.imageinfo() != null && !page.imageinfo().isEmpty()) {
                result.put(name, page.imageinfo().get(0));
            }
        }
        return result;
    }

    public record ImageInfo(int width, int height, String mime) {
    }

    record Response(Query query) {
        record Query(List<Normalized> normalized, Map<String, Page> pages) {
        }

        record Normalized(String from, String to) {
        }

        record Page(String title, List<ImageInfo> imageinfo) {
        }
    }
}
