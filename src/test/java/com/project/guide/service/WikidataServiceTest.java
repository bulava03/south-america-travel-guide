package com.project.guide.service;

import org.apache.jena.query.ResultSet;
import org.apache.jena.riot.ResultSetMgr;
import org.apache.jena.riot.resultset.ResultSetLang;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests parsing of a saved Wikidata SPARQL response, without network access.
 */
class WikidataServiceTest {

    private final WikidataService wikidataService = new WikidataService();

    @Test
    void testParseResultsGroupsRowsByAttraction() {
        Map<String, Map<String, Object>> attractions = parseFixture();

        assertEquals(2, attractions.size());
        assertTrue(attractions.containsKey("http://www.wikidata.org/entity/Q676203"));
        assertTrue(attractions.containsKey("http://www.wikidata.org/entity/Q193457"));
    }

    @Test
    void testParseResultsCollectsUniqueImages() {
        Map<String, Object> machuPicchu = parseFixture().get("http://www.wikidata.org/entity/Q676203");

        assertEquals(List.of(
                "http://commons.wikimedia.org/wiki/Special:FilePath/Machu%20Picchu%201.jpg",
                "http://commons.wikimedia.org/wiki/Special:FilePath/Machu%20Picchu%202.jpg"
        ), machuPicchu.get("images"));
    }

    @Test
    void testParseResultsReadsFieldsAndCoordinates() {
        Map<String, Object> iguazu = parseFixture().get("http://www.wikidata.org/entity/Q193457");

        assertEquals("Iguazu Falls", iguazu.get("name"));
        assertEquals("tourist attraction", iguazu.get("type"));
        assertEquals("Argentina", iguazu.get("country"));
        assertEquals("https://en.wikipedia.org/wiki/Iguazu_Falls", iguazu.get("wikipedia"));
        // WKT order is "Point(longitude latitude)"
        assertEquals("-25.6953", iguazu.get("latitude"));
        assertEquals("-54.4367", iguazu.get("longitude"));
    }

    @Test
    void testParseResultsSkipsAttractionsWithoutEnglishLabel() {
        assertFalse(parseFixture().containsKey("http://www.wikidata.org/entity/Q99999999"));
    }

    private Map<String, Map<String, Object>> parseFixture() {
        InputStream json = getClass().getResourceAsStream("/wikidata/attractions.json");
        ResultSet results = ResultSetMgr.read(json, ResultSetLang.RS_JSON);
        return wikidataService.parseResults(results);
    }
}
