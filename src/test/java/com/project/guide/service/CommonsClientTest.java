package com.project.guide.service;

import com.project.guide.config.CommonsProperties;
import com.project.guide.service.CommonsClient.ImageInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests the Commons image info client against a mocked HTTP server.
 */
@RestClientTest(
        components = CommonsClient.class,
        properties = "api.user-agent=TestAgent/1.0"
)
@EnableConfigurationProperties(CommonsProperties.class)
class CommonsClientTest {

    @Autowired
    private CommonsClient commonsClient;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void testImageInfoMatchesNormalizedTitlesAndSkipsMissingFiles() {
        server.expect(requestTo("https://commons.wikimedia.org/w/api.php"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("User-Agent", "TestAgent/1.0"))
                .andExpect(content().formDataContains(Map.of(
                        "titles", "File:Machu_Picchu.jpg|File:Iguaçu Falls.jpg|File:Deleted.jpg",
                        "iiprop", "size|mime"
                )))
                .andRespond(withSuccess("""
                        {"query": {
                          "normalized": [{"from": "File:Machu_Picchu.jpg", "to": "File:Machu Picchu.jpg"}],
                          "pages": {
                            "1": {"title": "File:Machu Picchu.jpg",
                                  "imageinfo": [{"size": 411550, "width": 1280, "height": 853, "mime": "image/jpeg"}]},
                            "2": {"title": "File:Iguaçu Falls.jpg",
                                  "imageinfo": [{"width": 4000, "height": 2465, "mime": "image/jpeg"}]},
                            "-1": {"title": "File:Deleted.jpg", "missing": ""}
                          }
                        }}
                        """, MediaType.APPLICATION_JSON));

        Map<String, ImageInfo> info = commonsClient.imageInfo(List.of("Machu_Picchu.jpg", "Iguaçu Falls.jpg", "Deleted.jpg"));

        assertEquals(Map.of(
                "Machu_Picchu.jpg", new ImageInfo(1280, 853, "image/jpeg"),
                "Iguaçu Falls.jpg", new ImageInfo(4000, 2465, "image/jpeg")
        ), info);
        server.verify();
    }
}
