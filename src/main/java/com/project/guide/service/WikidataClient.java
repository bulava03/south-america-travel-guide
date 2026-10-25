package com.project.guide.service;

import com.project.guide.config.WikidataProperties;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * HTTP access to Wikidata: search API and SPARQL query service.
 * Wikimedia rejects requests with the default Java User-Agent (HTTP 403), so a descriptive one is sent.
 */
@Component
public class WikidataClient {

    private final RestTemplate restTemplate;
    private final WikidataProperties properties;
    private final String userAgent;

    public WikidataClient(RestTemplateBuilder restTemplateBuilder, WikidataProperties properties,
                          @Value("${api.user-agent}") String userAgent) {
        this.restTemplate = restTemplateBuilder.defaultHeader(HttpHeaders.USER_AGENT, userAgent).build();
        this.properties = properties;
        this.userAgent = userAgent;
    }

    /**
     * Wikidata search (e.g. with haswbstatement filters).
     *
     * @return entity ids, most relevant first
     */
    public List<String> search(String query) {
        URI uri = UriComponentsBuilder.fromHttpUrl(properties.searchEndpoint())
                .queryParam("action", "query")
                .queryParam("list", "search")
                .queryParam("srsearch", "{query}")
                .queryParam("srlimit", properties.searchLimit())
                .queryParam("srnamespace", 0)
                .queryParam("srprop", "")
                .queryParam("format", "json")
                .encode()
                .buildAndExpand(query)
                .toUri();

        SearchResponse response = restTemplate.getForObject(uri, SearchResponse.class);
        if (response == null || response.query() == null) {
            throw new IllegalStateException("Unexpected Wikidata search response: " + response);
        }
        return response.query().search().stream().map(SearchResponse.Result::title).toList();
    }

    /**
     * Executes a SPARQL SELECT query; results are parsed before the connection is closed.
     */
    public <T> T select(String sparqlQuery, Function<ResultSet, T> parser) {
        try (QueryExecution execution = QueryExecutionHTTP.service(properties.sparqlEndpoint())
                .query(sparqlQuery)
                .httpHeader(HttpHeaders.USER_AGENT, userAgent)
                .timeout(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)
                .build()) {
            return parser.apply(execution.execSelect());
        }
    }

    record SearchResponse(Query query) {
        record Query(List<Result> search) {
        }

        record Result(String title) {
        }
    }
}
