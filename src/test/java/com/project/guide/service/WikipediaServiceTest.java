package com.project.guide.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests Wikipedia REST API client against a mocked HTTP server.
 */
@RestClientTest(
        components = WikipediaService.class,
        properties = "api.user-agent=TestAgent/1.0"
)
class WikipediaServiceTest {

    private static final String SUMMARY_API = "https://en.wikipedia.org/api/rest_v1/page/summary/";

    @Autowired
    private WikipediaService wikipediaService;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void testFetchWikipediaExtract() {
        server.expect(requestTo(SUMMARY_API + "Machu_Picchu"))
                .andExpect(header("User-Agent", "TestAgent/1.0"))
                .andRespond(withSuccess(
                        "{\"title\": \"Machu Picchu\", \"extract\": \"Machu Picchu is a 15th-century Inca citadel.\"}",
                        MediaType.APPLICATION_JSON
                ));

        String extract = wikipediaService.fetchWikipediaExtract("https://en.wikipedia.org/wiki/Machu_Picchu");

        assertEquals("Machu Picchu is a 15th-century Inca citadel.", extract);
        server.verify();
    }

    @Test
    void testFetchWikipediaExtractEncodesNonAsciiTitle() {
        server.expect(requestTo(SUMMARY_API + "Igua%C3%A7u_National_Park"))
                .andRespond(withSuccess("{\"extract\": \"Iguaçu National Park\"}", MediaType.APPLICATION_JSON));

        // Title may come both raw and already URL-encoded
        assertEquals("Iguaçu National Park",
                wikipediaService.fetchWikipediaExtract("https://en.wikipedia.org/wiki/Igua%C3%A7u_National_Park"));
        server.verify();
    }

    @Test
    void testFetchWikipediaExtractReturnsNullOnError() {
        server.expect(requestTo(SUMMARY_API + "Missing_Article"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertNull(wikipediaService.fetchWikipediaExtract("https://en.wikipedia.org/wiki/Missing_Article"));
        server.verify();
    }

    @Test
    void testFetchWikipediaExtractWithoutUrlMakesNoRequest() {
        assertNull(wikipediaService.fetchWikipediaExtract(null));
        assertNull(wikipediaService.fetchWikipediaExtract(""));
        server.verify();
    }
}
