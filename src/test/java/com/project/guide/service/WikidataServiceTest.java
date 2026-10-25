package com.project.guide.service;

import com.project.guide.config.WikidataProperties;
import com.project.guide.dto.WikidataAttraction;
import com.project.guide.dto.WikidataFetchResult;
import com.project.guide.service.SyncListener.CountryStep;
import org.apache.jena.query.ResultSet;
import org.apache.jena.riot.ResultSetMgr;
import org.apache.jena.riot.resultset.ResultSetLang;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests selection and parsing of saved Wikidata SPARQL responses, without network access.
 * The client is mocked where the whole fetch of a country is tested.
 * Uses the real catalog from wikidata.yml, since type priorities matter.
 */
class WikidataServiceTest {

    private static final String ENTITY = "http://www.wikidata.org/entity/";

    private final WikidataService wikidataService = new WikidataService(null, loadProperties());

    @Test
    void testSelectTopCandidatesByPopularityWithLimitPerType() {
        // Q2, Q3, Q4 are national parks: only the two most popular of them are taken
        List<String> selected = wikidataService.selectTopCandidates(fixture("candidates.json"), 25, 2, Set.of());

        assertEquals(List.of("Q1", "Q2", "Q3", "Q5", "Q7", "Q8", "Q6"), selected);
    }

    @Test
    void testVolcanoAndStratovolcanoShareTypeLimit() {
        // Q7 is a stratovolcano and Q8 a volcano: both are shown as "volcano"
        List<String> selected = wikidataService.selectTopCandidates(fixture("candidates.json"), 25, 1, Set.of());

        assertEquals(List.of("Q1", "Q2", "Q5", "Q7", "Q6"), selected);
    }

    @Test
    void testSelectTopCandidatesRespectsLimit() {
        List<String> selected = wikidataService.selectTopCandidates(fixture("candidates.json"), 3, 8, Set.of());

        assertEquals(List.of("Q1", "Q2", "Q3"), selected);
    }

    @Test
    void testSkippedCandidatesAreReplacedByNextPopular() {
        // Q2 is hidden: the third national park Q4 takes its place within the limit per type
        List<String> selected = wikidataService.selectTopCandidates(fixture("candidates.json"), 25, 2, Set.of("Q2"));

        assertFalse(selected.contains("Q2"));
        assertTrue(selected.containsAll(List.of("Q3", "Q4")));
        assertEquals(7, selected.size());
    }

    @Test
    void testFetchAttractionsReportsStepsAndSkipsFailedCountry() {
        WikidataClient client = mock(WikidataClient.class);
        WikidataService service = new WikidataService(client, loadProperties());
        when(client.search(contains("P17=Q298"))).thenThrow(new IllegalStateException("timed out"));
        when(client.search(contains("P17=Q419"))).thenReturn(List.of("Q1"));
        // The first query of Peru selects candidates, the second gets details
        AtomicInteger queries = new AtomicInteger();
        when(client.select(anyString(), any())).thenAnswer(invocation -> {
            Function<ResultSet, ?> parser = invocation.getArgument(1);
            return parser.apply(fixture(queries.getAndIncrement() == 0 ? "candidates.json" : "details.json"));
        });
        List<String> events = new ArrayList<>();
        SyncListener listener = new SyncListener() {
            @Override
            public void countryStep(String country, CountryStep step) {
                events.add(country + " " + step);
            }

            @Override
            public void countryDone(String country, int placeCount) {
                events.add(country + " done");
            }

            @Override
            public void countryFailed(String country, String message) {
                events.add(country + " failed: " + message);
            }
        };

        WikidataFetchResult result = service.fetchAttractions(List.of("Chile", "Peru"), Set.of(), listener);

        assertEquals(Set.of("Peru"), result.fetchedCountries().keySet());
        assertEquals(Map.of("Chile", "IllegalStateException: timed out"), result.failedCountries());
        assertEquals(2, result.attractions().size());
        // Chile is tried twice (attempts: 2 in wikidata.yml)
        assertEquals(List.of(
                "Chile SEARCH", "Chile SEARCH", "Chile failed: IllegalStateException: timed out",
                "Peru SEARCH", "Peru CANDIDATES", "Peru DETAILS", "Peru done"
        ), events);
    }

    @Test
    void testFetchAttractionsRejectsUnknownCountry() {
        assertThrows(IllegalArgumentException.class,
                () -> wikidataService.fetchAttractions(List.of("Mexico"), Set.of(), SyncListener.NONE));
    }

    @Test
    void testCountryNamesAreSorted() {
        List<String> names = wikidataService.countryNames();

        assertEquals(12, names.size());
        assertEquals("Argentina", names.get(0));
        assertEquals("Venezuela", names.get(11));
    }

    @Test
    void testParseDetailsMergesCountriesOfOneAttraction() {
        WikidataAttraction iguazu = parseDetails().get(ENTITY + "Q36332");

        assertEquals(Set.of("Argentina", "Brazil"), iguazu.getCountries());
        assertEquals(List.of("http://commons.wikimedia.org/wiki/Special:FilePath/Iguazu.jpg"), iguazu.getImages());
    }

    @Test
    void testParseDetailsReadsFields() {
        WikidataAttraction iguazu = parseDetails().get(ENTITY + "Q36332");

        assertEquals("Iguazu Falls", iguazu.getName());
        assertEquals("https://en.wikipedia.org/wiki/Iguazu_Falls", iguazu.getWikipediaUrl());
        assertEquals(99, iguazu.getPopularity());
        // WKT order is "Point(longitude latitude)"
        assertEquals(-25.695, iguazu.getLatitude());
        assertEquals(-54.436666, iguazu.getLongitude());
    }

    @Test
    void testParseDetailsUsesTypeWithHighestPriority() {
        Map<String, WikidataAttraction> attractions = parseDetails();

        assertEquals("waterfall", attractions.get(ENTITY + "Q36332").getType());
        assertEquals("archaeological site", attractions.get(ENTITY + "Q676203").getType());
    }

    @Test
    void testParseDetailsCollectsUniqueImagesAndIgnoresOtherCountries() {
        WikidataAttraction machuPicchu = parseDetails().get(ENTITY + "Q676203");

        assertEquals(Set.of("Peru"), machuPicchu.getCountries());
        assertEquals(List.of(
                "http://commons.wikimedia.org/wiki/Special:FilePath/Machu%20Picchu%201.jpg",
                "http://commons.wikimedia.org/wiki/Special:FilePath/Machu%20Picchu%202.jpg"
        ), machuPicchu.getImages());
    }

    @Test
    void testParseDetailsSkipsAttractionsWithoutLabel() {
        Map<String, WikidataAttraction> attractions = parseDetails();

        assertEquals(2, attractions.size());
        assertFalse(attractions.containsKey(ENTITY + "Q99999999"));
    }

    @Test
    void testSameAttractionFromTwoCountryQueriesIsNotDuplicated() {
        // Iguazu Falls is returned both by the Argentina and the Brazil query
        WikidataAttraction fromArgentina = parseDetails().get(ENTITY + "Q36332");
        WikidataAttraction fromBrazil = parseDetails().get(ENTITY + "Q36332");

        WikidataAttraction merged = fromArgentina.mergeWith(fromBrazil);

        assertEquals(1, merged.getImages().size());
        assertEquals(Set.of("Argentina", "Brazil"), merged.getCountries());
    }

    private Map<String, WikidataAttraction> parseDetails() {
        return wikidataService.parseDetails(fixture("details.json"));
    }

    private ResultSet fixture(String name) {
        return ResultSetMgr.read(getClass().getResourceAsStream("/wikidata/" + name), ResultSetLang.RS_JSON);
    }

    private static WikidataProperties loadProperties() {
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load("wikidata", new ClassPathResource("wikidata.yml"));
            return new Binder(ConfigurationPropertySources.from(sources), new PropertySourcesPlaceholdersResolver(sources))
                    .bind("wikidata", WikidataProperties.class)
                    .get();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
