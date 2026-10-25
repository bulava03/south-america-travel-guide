package com.project.guide.service;

import com.project.guide.config.WikidataProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests the Wikidata search API client against a mocked HTTP server.
 */
@RestClientTest(
        components = WikidataClient.class,
        properties = "api.user-agent=TestAgent/1.0"
)
@EnableConfigurationProperties(WikidataProperties.class)
class WikidataClientTest {

    @Autowired
    private WikidataClient wikidataClient;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void testSearchEncodesQueryAndReturnsIds() {
        server.expect(requestTo(allOf(
                        containsString("https://www.wikidata.org/w/api.php?action=query&list=search"),
                        containsString("srlimit=500")
                )))
                .andExpect(request -> {
                    // "=" and "|" in haswbstatement filters must survive URL encoding
                    String search = URLDecoder.decode(UriComponentsBuilder.fromUri(request.getURI()).build()
                            .getQueryParams().getFirst("srsearch"), StandardCharsets.UTF_8);
                    assertEquals("haswbstatement:P31=Q34038|P31=Q8502 haswbstatement:P17=Q414", search);
                })
                .andExpect(header("User-Agent", "TestAgent/1.0"))
                .andRespond(withSuccess("""
                        {"query": {"search": [
                          {"ns": 0, "title": "Q39739"},
                          {"ns": 0, "title": "Q36332"}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        List<String> ids = wikidataClient.search("haswbstatement:P31=Q34038|P31=Q8502 haswbstatement:P17=Q414");

        assertEquals(List.of("Q39739", "Q36332"), ids);
        server.verify();
    }

    @Test
    void testSearchFailsOnErrorResponse() {
        server.expect(requestTo(containsString("list=search")))
                .andRespond(withSuccess("{\"error\": {\"code\": \"maxlag\"}}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> wikidataClient.search("haswbstatement:P17=Q414 haswbstatement:P18"));
    }
}
