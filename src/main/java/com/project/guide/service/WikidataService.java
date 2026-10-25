package com.project.guide.service;

import com.project.guide.dto.WikidataAttraction;
import com.project.guide.dto.WikidataFetchResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Service for fetching attractions from Wikidata.
 * Fetches the most popular tourist attractions of each South American country.
 *
 * A single SPARQL query that finds attractions by type and country is too slow for the
 * 60 s Wikidata Query Service limit: it scans e.g. all lakes in the world. So each country
 * is fetched in three fast steps:
 * 1. Wikidata search (haswbstatement filters) finds candidates with a type, image and coordinates;
 * 2. SPARQL gets types and popularity (number of Wikipedia language editions) of the candidates,
 *    keeps only those with an English Wikipedia article; the most popular ones are selected
 *    in Java, with a limit per type for variety;
 * 3. SPARQL gets details (name, coordinates, images, article) of the selected attractions.
 */
@Service
@Slf4j
public class WikidataService {

    private static final String ENTITY_PREFIX = "http://www.wikidata.org/entity/";

    /**
     * Attempts per country: Wikidata Query Service sometimes answers with 502/504 under load.
     */
    private static final int ATTEMPTS = 2;

    /**
     * South American countries: Wikidata id -> name.
     */
    static final Map<String, String> COUNTRIES = new LinkedHashMap<>();

    /**
     * Attraction types: Wikidata id -> label, in order of priority.
     * When an attraction has several types, the first one from this list is shown.
     */
    static final Map<String, String> TYPES = new LinkedHashMap<>();

    /**
     * Types that get into results via "historic site" but are not attractions.
     */
    private static final List<String> EXCLUDED_TYPES = List.of(
            "Q476028",  // association football club
            "Q847017",  // sports club
            "Q483110",  // stadium
            "Q43229",   // organization
            "Q31855"    // research institute
    );

    static {
        COUNTRIES.put("Q414", "Argentina");
        COUNTRIES.put("Q155", "Brazil");
        COUNTRIES.put("Q750", "Bolivia");
        COUNTRIES.put("Q298", "Chile");
        COUNTRIES.put("Q739", "Colombia");
        COUNTRIES.put("Q736", "Ecuador");
        COUNTRIES.put("Q734", "Guyana");
        COUNTRIES.put("Q733", "Paraguay");
        COUNTRIES.put("Q419", "Peru");
        COUNTRIES.put("Q730", "Suriname");
        COUNTRIES.put("Q77", "Uruguay");
        COUNTRIES.put("Q717", "Venezuela");

        // Nature
        TYPES.put("Q34038", "waterfall");
        TYPES.put("Q35666", "glacier");
        TYPES.put("Q935277", "salt flat");
        TYPES.put("Q23397", "lake");
        TYPES.put("Q8072", "volcano");
        TYPES.put("Q169358", "volcano");      // stratovolcano
        TYPES.put("Q8502", "mountain");
        TYPES.put("Q150784", "canyon");
        TYPES.put("Q39816", "valley");
        TYPES.put("Q8514", "desert");
        TYPES.put("Q33837", "archipelago");
        TYPES.put("Q23442", "island");
        TYPES.put("Q40080", "beach");
        TYPES.put("Q46169", "national park");
        // Culture
        TYPES.put("Q839954", "archaeological site");
        TYPES.put("Q2977", "cathedral");
        TYPES.put("Q153562", "opera house");
        TYPES.put("Q16560", "palace");
        TYPES.put("Q57821", "fortress");
        TYPES.put("Q16970", "church");
        TYPES.put("Q33506", "museum");
        TYPES.put("Q4989906", "monument");
        TYPES.put("Q174782", "square");
        TYPES.put("Q1081138", "historic site");
        TYPES.put("Q9259", "World Heritage Site");
        TYPES.put("Q570116", "tourist attraction");
    }

    private final RestTemplate restTemplate;

    @Value("${api.wikidata.endpoint}")
    private String wikidataEndpoint;

    @Value("${api.wikidata.search-endpoint}")
    private String searchEndpoint;

    @Value("${api.wikidata.timeout}")
    private int timeout;

    @Value("${api.user-agent}")
    private String userAgent;

    @Value("${app.sync.search-limit}")
    private int searchLimit;

    @Value("${app.sync.per-country-limit}")
    private int perCountryLimit;

    @Value("${app.sync.max-per-type}")
    private int maxPerType;

    public WikidataService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    /**
     * Fetches the most popular attractions of every country.
     * A failed country does not stop the others; it is reported in the result.
     *
     * @throws RuntimeException if all countries failed
     */
    public WikidataFetchResult fetchAttractions() {
        log.info("Starting to fetch attractions from Wikidata...");

        Map<String, WikidataAttraction> attractions = new LinkedHashMap<>();
        List<String> failedCountries = new ArrayList<>();

        for (Map.Entry<String, String> country : COUNTRIES.entrySet()) {
            try {
                int fetched = withRetry(country.getValue(), () -> fetchCountry(country.getKey(), attractions));
                log.info("Fetched {} attractions for {}", fetched, country.getValue());
            } catch (Exception e) {
                log.error("Error fetching attractions for {} from Wikidata", country.getValue(), e);
                failedCountries.add(country.getValue());
            }
        }

        if (failedCountries.size() == COUNTRIES.size()) {
            throw new RuntimeException("Failed to fetch attractions from Wikidata");
        }

        log.info("Successfully fetched {} attractions, failed countries: {}", attractions.size(), failedCountries);
        return new WikidataFetchResult(new ArrayList<>(attractions.values()), failedCountries);
    }

    /**
     * Fetches one country and merges its attractions into {@code attractions}.
     *
     * @return number of selected attractions
     */
    private int fetchCountry(String countryId, Map<String, WikidataAttraction> attractions) {
        List<String> candidates = searchCandidates(countryId);
        if (candidates.isEmpty()) {
            return 0;
        }

        List<String> selected = query(buildCandidatesQuery(candidates),
                results -> selectTopCandidates(results, perCountryLimit, maxPerType));
        if (selected.isEmpty()) {
            return 0;
        }

        // Parse into a copy, so a failed attempt does not leave partial data
        Map<String, WikidataAttraction> fetched = new LinkedHashMap<>();
        query(buildDetailsQuery(selected), results -> {
            parseDetails(results, fetched);
            return null;
        });
        fetched.forEach((uri, attraction) -> attractions.merge(uri, attraction, this::mergeAttractions));
        return selected.size();
    }

    /**
     * Step 1: candidates from Wikidata search, roughly sorted by relevance (popular items first).
     *
     * @return Wikidata ids
     */
    List<String> searchCandidates(String countryId) {
        String types = TYPES.keySet().stream()
                .map(id -> "P31=" + id)
                .collect(Collectors.joining("|"));
        String search = "haswbstatement:" + types
                + " haswbstatement:P17=" + countryId
                + " haswbstatement:P18 haswbstatement:P625";

        URI uri = UriComponentsBuilder.fromHttpUrl(searchEndpoint)
                .queryParam("action", "query")
                .queryParam("list", "search")
                .queryParam("srsearch", "{search}")
                .queryParam("srlimit", searchLimit)
                .queryParam("srnamespace", 0)
                .queryParam("srprop", "")
                .queryParam("format", "json")
                .encode()
                .buildAndExpand(search)
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", userAgent);

        Map<?, ?> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), Map.class)
                .getBody();
        if (response == null || !(response.get("query") instanceof Map<?, ?> query)) {
            throw new IllegalStateException("Unexpected Wikidata search response: " + response);
        }

        return ((List<?>) query.get("search")).stream()
                .map(result -> (String) ((Map<?, ?>) result).get("title"))
                .collect(Collectors.toList());
    }

    /**
     * Selects the most popular candidates, at most {@code maxPerType} of the same type.
     * Rows contain ?item, ?type and ?sitelinks; one attraction may come in several rows, one per type.
     *
     * @return Wikidata ids of the selected attractions, most popular first
     */
    List<String> selectTopCandidates(ResultSet results, int limit, int maxPerType) {
        Map<String, Set<String>> typesById = new HashMap<>();
        Map<String, Integer> popularityById = new HashMap<>();

        while (results.hasNext()) {
            QuerySolution solution = results.next();
            String id = toEntityId(getStringValue(solution, "item"));
            typesById.computeIfAbsent(id, key -> new HashSet<>())
                    .add(toEntityId(getStringValue(solution, "type")));
            popularityById.put(id, Integer.parseInt(getStringValue(solution, "sitelinks")));
        }

        List<String> byPopularity = popularityById.keySet().stream()
                .sorted(Comparator.comparing((String id) -> popularityById.get(id)).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .collect(Collectors.toList());

        List<String> selected = new ArrayList<>();
        Map<String, Integer> countByType = new HashMap<>();

        for (String id : byPopularity) {
            if (selected.size() >= limit) {
                break;
            }
            // Count by label, so volcanoes and stratovolcanoes share one limit
            String type = TYPES.get(primaryType(typesById.get(id)));
            if (countByType.getOrDefault(type, 0) >= maxPerType) {
                continue;
            }
            countByType.merge(type, 1, Integer::sum);
            selected.add(id);
        }

        return selected;
    }

    /**
     * Merges detail rows into {@code attractions} by Wikidata URI.
     * Rows contain ?item, ?itemLabel, ?type, ?country, ?coord, ?image, ?article and ?sitelinks;
     * one attraction comes in a row for every combination of its types, countries and images.
     */
    void parseDetails(ResultSet results, Map<String, WikidataAttraction> attractions) {
        Map<String, Set<String>> typesByUri = new HashMap<>();

        while (results.hasNext()) {
            QuerySolution solution = results.next();

            String uri = getStringValue(solution, "item");
            String name = getStringValue(solution, "itemLabel");

            // Label service returns the entity id (e.g. "Q12345") when there is no label
            if (name == null || name.matches("Q\\d+")) {
                continue;
            }

            WikidataAttraction attraction = attractions.computeIfAbsent(uri, key -> {
                WikidataAttraction created = new WikidataAttraction();
                created.setUri(key);
                created.setName(name);
                created.setWikipediaUrl(getStringValue(solution, "article"));
                created.setPopularity(Integer.parseInt(getStringValue(solution, "sitelinks")));

                double[] lonLat = parseCoordinates(getStringValue(solution, "coord"));
                if (lonLat != null) {
                    created.setLongitude(lonLat[0]);
                    created.setLatitude(lonLat[1]);
                }
                return created;
            });

            String country = COUNTRIES.get(toEntityId(getStringValue(solution, "country")));
            if (country != null) {
                attraction.getCountries().add(country);
            }

            String imageUrl = getStringValue(solution, "image");
            if (imageUrl != null && !attraction.getImages().contains(imageUrl)) {
                attraction.getImages().add(imageUrl);
            }

            typesByUri.computeIfAbsent(uri, key -> new HashSet<>())
                    .add(toEntityId(getStringValue(solution, "type")));
        }

        typesByUri.forEach((uri, types) -> {
            WikidataAttraction attraction = attractions.get(uri);
            if (attraction.getType() == null) {
                attraction.setType(TYPES.get(primaryType(types)));
            }
        });
    }

    /**
     * Step 2: types and popularity of the candidates that have an English Wikipedia article.
     */
    private String buildCandidatesQuery(List<String> ids) {
        return """
        PREFIX wd: <http://www.wikidata.org/entity/>
        PREFIX wdt: <http://www.wikidata.org/prop/direct/>
        PREFIX schema: <http://schema.org/>
        PREFIX wikibase: <http://wikiba.se/ontology#>

        SELECT ?item ?type ?sitelinks
        WHERE {
          VALUES ?item { %s }
          VALUES ?type { %s }

          ?item wdt:P31 ?type;
                wikibase:sitelinks ?sitelinks.

          # English Wikipedia article
          ?article schema:about ?item;
                   schema:isPartOf <https://en.wikipedia.org/>.

          FILTER NOT EXISTS {
            VALUES ?excluded { %s }
            ?item wdt:P31 ?excluded.
          }
        }
        """.formatted(toValues(ids), toValues(TYPES.keySet()), toValues(EXCLUDED_TYPES));
    }

    /**
     * Step 3: details of the selected attractions.
     */
    private String buildDetailsQuery(List<String> ids) {
        return """
        PREFIX wd: <http://www.wikidata.org/entity/>
        PREFIX wdt: <http://www.wikidata.org/prop/direct/>
        PREFIX schema: <http://schema.org/>
        PREFIX wikibase: <http://wikiba.se/ontology#>
        PREFIX bd: <http://www.bigdata.com/rdf#>

        SELECT ?item ?itemLabel ?type ?country ?coord ?image ?article ?sitelinks
        WHERE {
          VALUES ?item { %s }
          VALUES ?type { %s }

          ?item wdt:P31 ?type;
                wdt:P17 ?country;
                wdt:P625 ?coord;
                wdt:P18 ?image;
                wikibase:sitelinks ?sitelinks.

          ?article schema:about ?item;
                   schema:isPartOf <https://en.wikipedia.org/>.

          # Many items have only a multilingual ("mul") label instead of an English one
          SERVICE wikibase:label { bd:serviceParam wikibase:language "en,mul". }
        }
        """.formatted(toValues(ids), toValues(TYPES.keySet()));
    }

    /**
     * Executes a SELECT query and parses the results before the connection is closed.
     */
    private <T> T query(String sparqlQuery, Function<ResultSet, T> parser) {
        // Wikimedia rejects requests with the default Java User-Agent (HTTP 403)
        try (QueryExecution qexec = QueryExecutionHTTP.service(wikidataEndpoint)
                .query(sparqlQuery)
                .httpHeader("User-Agent", userAgent)
                .timeout(timeout, TimeUnit.MILLISECONDS)
                .build()) {
            return parser.apply(qexec.execSelect());
        }
    }

    private <T> T withRetry(String countryName, Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                if (attempt >= ATTEMPTS) {
                    throw e;
                }
                log.warn("Attempt {} for {} failed: {}, retrying", attempt, countryName, e.getMessage());
            }
        }
    }

    /**
     * Combines the same attraction returned for two countries.
     */
    private WikidataAttraction mergeAttractions(WikidataAttraction existing, WikidataAttraction other) {
        existing.getCountries().addAll(other.getCountries());
        other.getImages().stream()
                .filter(image -> !existing.getImages().contains(image))
                .forEach(existing.getImages()::add);
        return existing;
    }

    /**
     * Returns the type with the highest priority from {@link #TYPES}.
     */
    private String primaryType(Set<String> typeIds) {
        return TYPES.keySet().stream()
                .filter(typeIds::contains)
                .findFirst()
                .orElse(null);
    }

    private String toValues(Collection<String> ids) {
        return ids.stream().map(id -> "wd:" + id).collect(Collectors.joining(" "));
    }

    private String toEntityId(String uri) {
        return uri == null ? null : uri.replace(ENTITY_PREFIX, "");
    }

    /**
     * Safely extracts string value from QuerySolution.
     */
    private String getStringValue(QuerySolution solution, String varName) {
        if (solution.contains(varName)) {
            RDFNode node = solution.get(varName);
            if (node != null) {
                if (node.isLiteral()) {
                    return node.asLiteral().getString();
                } else {
                    return node.toString();
                }
            }
        }
        return null;
    }

    /**
     * Parses WKT coordinate string "Point(longitude latitude)" into array [longitude, latitude].
     */
    private double[] parseCoordinates(String coords) {
        if (coords == null || !coords.startsWith("Point(")) {
            return null;
        }
        try {
            String[] parts = coords.replace("Point(", "").replace(")", "").split(" ");
            if (parts.length == 2) {
                return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to parse coordinates: {}", coords);
        }
        return null;
    }
}
