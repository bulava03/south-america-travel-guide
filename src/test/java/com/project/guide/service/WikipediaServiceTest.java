package com.project.guide.service;

import com.project.guide.dto.WikidataAttraction;
import com.project.guide.config.WikimediaClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests the Wikipedia extracts client against a mocked HTTP server.
 */
@Import(WikimediaClientConfig.class)
@RestClientTest(
        components = WikipediaService.class,
        properties = "api.user-agent=TestAgent/1.0"
)
class WikipediaServiceTest {

    @Autowired
    private WikipediaService wikipediaService;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void testAddDescriptionsResolvesNormalizedAndRedirectedTitles() {
        server.expect(requestTo(WikipediaService.API))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("User-Agent", "TestAgent/1.0"))
                .andExpect(content().formDataContains(Map.of(
                        "titles", "Machu_Picchu|Iguaçu_National_Park|Deleted_Article",
                        "prop", "extracts"
                )))
                .andRespond(withSuccess("""
                        {"batchcomplete": true, "query": {
                          "normalized": [
                            {"from": "Machu_Picchu", "to": "Machu Picchu"},
                            {"from": "Iguaçu_National_Park", "to": "Iguaçu National Park"},
                            {"from": "Deleted_Article", "to": "Deleted Article"}
                          ],
                          "redirects": [{"from": "Iguaçu National Park", "to": "Iguazu National Park"}],
                          "pages": [
                            {"pageid": 1, "title": "Machu Picchu",
                             "extract": "Machu Picchu is a 15th-century Inca citadel.\\nIt is in Peru."},
                            {"pageid": 2, "title": "Iguazu National Park", "extract": "\\nA national park in Argentina."},
                            {"title": "Deleted Article", "missing": true}
                          ]
                        }}
                        """, MediaType.APPLICATION_JSON));

        WikidataAttraction machuPicchu = attraction("https://en.wikipedia.org/wiki/Machu_Picchu");
        // Title may come URL-encoded
        WikidataAttraction iguazu = attraction("https://en.wikipedia.org/wiki/Igua%C3%A7u_National_Park");
        WikidataAttraction deleted = attraction("https://en.wikipedia.org/wiki/Deleted_Article");
        WikidataAttraction withoutArticle = attraction(null);

        wikipediaService.addDescriptions(List.of(machuPicchu, iguazu, deleted, withoutArticle));

        // Only the first paragraph of the intro is kept
        assertEquals("Machu Picchu is a 15th-century Inca citadel.", machuPicchu.getDescription());
        assertEquals("A national park in Argentina.", iguazu.getDescription());
        assertNull(deleted.getDescription());
        assertNull(withoutArticle.getDescription());
        server.verify();
    }

    @Test
    void testAddDescriptionsInBatchesAndSkipsFailedBatch() {
        List<WikidataAttraction> attractions = IntStream.range(0, WikipediaService.BATCH_SIZE + 1)
                .mapToObj(i -> attraction("https://en.wikipedia.org/wiki/Place" + i))
                .toList();
        server.expect(ExpectedCount.once(), requestTo(WikipediaService.API))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(ExpectedCount.once(), requestTo(WikipediaService.API))
                .andExpect(content().formDataContains(Map.of("titles", "Place20")))
                .andRespond(withSuccess("""
                        {"query": {"pages": [{"title": "Place20", "extract": "The last place."}]}}
                        """, MediaType.APPLICATION_JSON));

        wikipediaService.addDescriptions(attractions);

        assertNull(attractions.get(0).getDescription());
        assertEquals("The last place.", attractions.get(WikipediaService.BATCH_SIZE).getDescription());
        server.verify();
    }

    @Test
    void testAddDescriptionsWithoutArticlesMakesNoRequest() {
        wikipediaService.addDescriptions(List.of(attraction(null), attraction("")));
        server.verify();
    }

    @Test
    void testTitle() {
        assertEquals("AC/DC", WikipediaService.title("https://en.wikipedia.org/wiki/AC/DC"));
        assertEquals("Iguaçu_Falls", WikipediaService.title("https://en.wikipedia.org/wiki/Igua%C3%A7u_Falls"));
        assertNull(WikipediaService.title("https://en.wikipedia.org/wiki/"));
        assertNull(WikipediaService.title(null));
    }

    private static WikidataAttraction attraction(String wikipediaUrl) {
        WikidataAttraction attraction = new WikidataAttraction();
        attraction.setWikipediaUrl(wikipediaUrl);
        return attraction;
    }
}
