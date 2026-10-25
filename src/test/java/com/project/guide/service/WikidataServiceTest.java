package com.project.guide.service;

import com.project.guide.config.WikidataProperties;
import com.project.guide.dto.WikidataAttraction;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests selection and parsing of saved Wikidata SPARQL responses, without network access.
 * Uses the real catalog from wikidata.yml, since type priorities matter.
 */
class WikidataServiceTest {

    private static final String ENTITY = "http://www.wikidata.org/entity/";

    private final WikidataService wikidataService = new WikidataService(null, loadProperties());

    @Test
    void testSelectTopCandidatesByPopularityWithLimitPerType() {
        // Q2, Q3, Q4 are national parks: only the two most popular of them are taken
        List<String> selected = wikidataService.selectTopCandidates(fixture("candidates.json"), 25, 2);

        assertEquals(List.of("Q1", "Q2", "Q3", "Q5", "Q7", "Q8", "Q6"), selected);
    }

    @Test
    void testVolcanoAndStratovolcanoShareTypeLimit() {
        // Q7 is a stratovolcano and Q8 a volcano: both are shown as "volcano"
        List<String> selected = wikidataService.selectTopCandidates(fixture("candidates.json"), 25, 1);

        assertEquals(List.of("Q1", "Q2", "Q5", "Q7", "Q6"), selected);
    }

    @Test
    void testSelectTopCandidatesRespectsLimit() {
        List<String> selected = wikidataService.selectTopCandidates(fixture("candidates.json"), 3, 8);

        assertEquals(List.of("Q1", "Q2", "Q3"), selected);
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
