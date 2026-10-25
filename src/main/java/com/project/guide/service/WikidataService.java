package com.project.guide.service;

import com.project.guide.config.WikidataProperties;
import com.project.guide.config.WikidataProperties.AttractionType;
import com.project.guide.dto.WikidataAttraction;
import com.project.guide.dto.WikidataFetchResult;
import com.project.guide.service.SyncListener.CountryStep;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.RDFNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Fetches the most popular attractions of each South American country from Wikidata.
 *
 * A single SPARQL query by type and country is too slow for the 60 s query service limit
 * (it scans e.g. all lakes in the world), so each country is fetched in three fast steps:
 * 1. Wikidata search finds candidates with a type, image and coordinates;
 * 2. SPARQL (sparql/candidates.rq) gets their types and popularity, the number of Wikipedia
 *    language editions; the most popular ones are selected, with a limit per type for variety;
 * 3. SPARQL (sparql/details.rq) gets details of the selected attractions.
 * Attractions hidden by the admin are skipped before the selection, so the next popular ones take their places.
 */
@Service
@Slf4j
public class WikidataService {

    private static final String ENTITY_PREFIX = "http://www.wikidata.org/entity/";

    private final WikidataClient client;
    private final WikidataProperties properties;
    /** Type id -> label, in order of priority. */
    private final Map<String, String> typeLabels;
    private final String candidatesQuery = loadQuery("candidates");
    private final String detailsQuery = loadQuery("details");

    public WikidataService(WikidataClient client, WikidataProperties properties) {
        this.client = client;
        this.properties = properties;
        this.typeLabels = properties.types().stream()
                .collect(Collectors.toMap(AttractionType::id, AttractionType::label, (a, b) -> a, LinkedHashMap::new));
    }

    public List<String> countryNames() {
        return properties.countries().values().stream().sorted().toList();
    }

    /**
     * Fetches the given countries one by one. A failed country is retried, then skipped and
     * reported in the result; if all of them failed, the result is empty.
     *
     * @param countryNames countries from wikidata.yml
     * @param skippedUris  Wikidata URIs of attractions that must not be selected
     */
    public WikidataFetchResult fetchAttractions(Collection<String> countryNames, Set<String> skippedUris,
                                                SyncListener listener) {
        Map<String, String> idsByName = properties.countries().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        Set<String> skippedIds = skippedUris.stream()
                .map(uri -> uri.replace(ENTITY_PREFIX, ""))
                .collect(Collectors.toSet());

        Map<String, WikidataAttraction> attractions = new LinkedHashMap<>();
        Map<String, Integer> fetched = new LinkedHashMap<>();
        Map<String, String> failed = new LinkedHashMap<>();

        for (String countryName : countryNames) {
            String countryId = idsByName.get(countryName);
            if (countryId == null) {
                throw new IllegalArgumentException("Unknown country: " + countryName);
            }
            try {
                int count = withRetry(countryName,
                        () -> fetchCountry(countryId, countryName, skippedIds, attractions, listener));
                log.info("Fetched {} attractions for {}", count, countryName);
                fetched.put(countryName, count);
                listener.countryDone(countryName, count);
            } catch (RuntimeException e) {
                log.error("Error fetching attractions for {} from Wikidata", countryName, e);
                failed.put(countryName, describe(e));
                listener.countryFailed(countryName, describe(e));
            }
        }

        log.info("Fetched {} attractions, failed countries: {}", attractions.size(), failed.keySet());
        return new WikidataFetchResult(new ArrayList<>(attractions.values()), fetched, failed);
    }

    /**
     * @return number of selected attractions
     */
    private int fetchCountry(String countryId, String countryName, Set<String> skippedIds,
                             Map<String, WikidataAttraction> attractions, SyncListener listener) {
        listener.countryStep(countryName, CountryStep.SEARCH);
        List<String> candidates = client.search(searchQuery(countryId));
        if (candidates.isEmpty()) {
            return 0;
        }

        listener.countryStep(countryName, CountryStep.CANDIDATES);
        List<String> selected = client.select(render(candidatesQuery, candidates),
                results -> selectTopCandidates(results, properties.perCountryLimit(), properties.maxPerType(), skippedIds));
        if (selected.isEmpty()) {
            return 0;
        }

        // Merged only after the whole country succeeded, so a failed attempt leaves no partial data
        listener.countryStep(countryName, CountryStep.DETAILS);
        client.select(render(detailsQuery, selected), this::parseDetails)
                .forEach((uri, attraction) -> attractions.merge(uri, attraction, WikidataAttraction::mergeWith));
        return selected.size();
    }

    /**
     * Candidates of a country with one of the types, an image (P18) and coordinates (P625).
     */
    private String searchQuery(String countryId) {
        String types = typeLabels.keySet().stream().map(id -> "P31=" + id).collect(Collectors.joining("|"));
        return "haswbstatement:" + types + " haswbstatement:P17=" + countryId
                + " haswbstatement:P18 haswbstatement:P625";
    }

    /**
     * Selects the most popular candidates, at most {@code maxPerType} with the same type label;
     * skipped ids are not counted. Rows contain ?item, ?type and ?sitelinks, one row per type.
     *
     * @return Wikidata ids, most popular first
     */
    List<String> selectTopCandidates(ResultSet results, int limit, int maxPerType, Set<String> skippedIds) {
        Map<String, Set<String>> typesById = new HashMap<>();
        Map<String, Integer> popularityById = new HashMap<>();
        results.forEachRemaining(row -> {
            String id = entityId(row, "item");
            typesById.computeIfAbsent(id, key -> new HashSet<>()).add(entityId(row, "type"));
            popularityById.put(id, Integer.parseInt(value(row, "sitelinks")));
        });

        Map<String, Integer> countByType = new HashMap<>();
        return popularityById.keySet().stream()
                .filter(id -> !skippedIds.contains(id))
                .sorted(Comparator.comparing((String id) -> popularityById.get(id)).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .filter(id -> countByType.merge(primaryType(typesById.get(id)), 1, Integer::sum) <= maxPerType)
                .limit(limit)
                .toList();
    }

    /**
     * Groups detail rows by Wikidata URI; one attraction comes in a row
     * for every combination of its types, countries and images.
     */
    Map<String, WikidataAttraction> parseDetails(ResultSet results) {
        Map<String, WikidataAttraction> attractions = new LinkedHashMap<>();
        Map<String, Set<String>> typesByUri = new HashMap<>();

        results.forEachRemaining(row -> {
            String uri = value(row, "item");
            String name = value(row, "itemLabel");
            // Label service returns the entity id (e.g. "Q12345") when there is no label
            if (name == null || name.matches("Q\\d+")) {
                return;
            }

            WikidataAttraction attraction = attractions.computeIfAbsent(uri, key -> newAttraction(key, name, row));
            Optional.ofNullable(properties.countries().get(entityId(row, "country")))
                    .ifPresent(attraction.getCountries()::add);
            String image = value(row, "image");
            if (!attraction.getImages().contains(image)) {
                attraction.getImages().add(image);
            }
            typesByUri.computeIfAbsent(uri, key -> new HashSet<>()).add(entityId(row, "type"));
        });

        typesByUri.forEach((uri, types) -> attractions.get(uri).setType(primaryType(types)));
        return attractions;
    }

    private WikidataAttraction newAttraction(String uri, String name, QuerySolution row) {
        WikidataAttraction attraction = new WikidataAttraction();
        attraction.setUri(uri);
        attraction.setName(name);
        attraction.setWikipediaUrl(value(row, "article"));
        attraction.setPopularity(Integer.parseInt(value(row, "sitelinks")));

        // WKT literal "Point(longitude latitude)"
        String[] lonLat = value(row, "coord").replaceAll("^Point\\(|\\)$", "").split(" ");
        try {
            attraction.setLongitude(Double.parseDouble(lonLat[0]));
            attraction.setLatitude(Double.parseDouble(lonLat[1]));
        } catch (RuntimeException e) {
            log.warn("Failed to parse coordinates of {}", uri);
        }
        return attraction;
    }

    /**
     * Label of the type with the highest priority.
     */
    private String primaryType(Set<String> typeIds) {
        return typeLabels.entrySet().stream()
                .filter(type -> typeIds.contains(type.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String render(String query, List<String> items) {
        return query.replace("{{items}}", toValues(items))
                .replace("{{types}}", toValues(typeLabels.keySet()))
                .replace("{{excluded}}", toValues(properties.excludedTypes()));
    }

    private <T> T withRetry(String countryName, Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                if (attempt >= properties.attempts()) {
                    throw e;
                }
                log.warn("Attempt {} for {} failed: {}, retrying", attempt, countryName, e.getMessage());
            }
        }
    }

    /**
     * Short error for the admin panel, e.g. "HttpTimeoutException: request timed out".
     */
    private static String describe(RuntimeException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        String message = cause.getMessage() == null ? "" : ": " + cause.getMessage();
        String text = cause.getClass().getSimpleName() + message;
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    private static String toValues(Collection<String> ids) {
        return ids.stream().map(id -> "wd:" + id).collect(Collectors.joining(" "));
    }

    private static String value(QuerySolution row, String variable) {
        RDFNode node = row.get(variable);
        if (node == null) {
            return null;
        }
        return node.isLiteral() ? node.asLiteral().getString() : node.toString();
    }

    private static String entityId(QuerySolution row, String variable) {
        String uri = value(row, variable);
        return uri == null ? null : uri.replace(ENTITY_PREFIX, "");
    }

    private static String loadQuery(String name) {
        try {
            return new ClassPathResource("sparql/" + name + ".rq").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
