package com.project.guide.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests the Wikidata search step against a mocked HTTP server.
 */
@RestClientTest(
        components = WikidataService.class,
        properties = {"api.user-agent=TestAgent/1.0", "app.sync.search-limit=500"}
)
class WikidataSearchTest {

    @Autowired
    private WikidataService wikidataService;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void testSearchCandidatesBuildsQueryAndReturnsIds() {
        server.expect(requestTo(allOf(
                        containsString("https://www.wikidata.org/w/api.php?action=query&list=search"),
                        containsString("srlimit=500")
                )))
                .andExpect(request -> {
                    String search = URLDecoder.decode(UriComponentsBuilder.fromUri(request.getURI()).build()
                            .getQueryParams().getFirst("srsearch"), StandardCharsets.UTF_8);
                    assertThat(search, allOf(
                            startsWith("haswbstatement:P31=Q34038|P31=Q35666|"),
                            containsString(" haswbstatement:P17=Q414"),
                            endsWith(" haswbstatement:P18 haswbstatement:P625")
                    ));
                })
                .andExpect(header("User-Agent", "TestAgent/1.0"))
                .andRespond(withSuccess("""
                        {"query": {"search": [
                          {"ns": 0, "title": "Q39739"},
                          {"ns": 0, "title": "Q36332"}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        List<String> ids = wikidataService.searchCandidates("Q414");

        assertEquals(List.of("Q39739", "Q36332"), ids);
        server.verify();
    }

    @Test
    void testSearchCandidatesFailsOnErrorResponse() {
        server.expect(requestTo(containsString("list=search")))
                .andRespond(withSuccess("{\"error\": {\"code\": \"maxlag\"}}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> wikidataService.searchCandidates("Q414"));
    }
}
