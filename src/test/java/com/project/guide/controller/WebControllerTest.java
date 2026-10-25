package com.project.guide.controller;

import com.project.guide.config.SecurityConfig;
import com.project.guide.dto.AdminAttractionDTO;
import com.project.guide.dto.AdminFilter;
import com.project.guide.dto.AttractionDTO;
import com.project.guide.dto.AttractionDetailDTO;
import com.project.guide.dto.PhotoLayout;
import com.project.guide.exception.AttractionNotFoundException;
import com.project.guide.service.AttractionService;
import com.project.guide.service.WikidataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pages are rendered with the real Thymeleaf templates.
 */
@WebMvcTest(
        controllers = WebController.class,
        properties = {"app.admin.username=admin", "app.admin.password=test"}
)
@Import(SecurityConfig.class)
class WebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttractionService attractionService;

    @MockBean
    private WikidataService wikidataService;

    @Test
    void testHomePage() throws Exception {
        when(attractionService.getAllCountries()).thenReturn(List.of("Argentina", "Brazil"));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("/places?country=Brazil")));
    }

    @Test
    void testPlacesPage() throws Exception {
        when(attractionService.findAttractions("Peru", null)).thenReturn(List.of(new AttractionDTO(
                1L, "Machu Picchu", "archaeological site", List.of("Peru"), -13.1631, -72.5450, "http://example.com/a.jpg", PhotoLayout.COVER
        )));
        when(attractionService.getAllCountries()).thenReturn(List.of("Chile", "Peru"));

        mockMvc.perform(get("/places").param("country", "Peru"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedCountry", "Peru"))
                .andExpect(content().string(containsString("Machu Picchu")))
                // a thumbnail instead of the original file
                .andExpect(content().string(containsString("https://example.com/a.jpg?width=800")));
    }

    @Test
    void testAttractionDetailsPage() throws Exception {
        when(attractionService.getAttractionDetails(1L)).thenReturn(new AttractionDetailDTO(
                1L, "Machu Picchu", "archaeological site", List.of("Peru"), -13.1631, -72.5450,
                "http://example.com/a.jpg", PhotoLayout.COVER, "Inca citadel",
                "https://en.wikipedia.org/wiki/Machu_Picchu", "http://www.wikidata.org/entity/Q676203",
                List.of("http://example.com/a.jpg")
        ));

        when(attractionService.findMoreInCountry(any(), anyInt())).thenReturn(List.of(new AttractionDTO(
                2L, "Titicaca", "lake", List.of("Bolivia", "Peru"), -15.825, -69.325, null, PhotoLayout.NONE
        )));

        mockMvc.perform(get("/attraction/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Inca citadel")))
                .andExpect(content().string(containsString("13.1631° S")))
                .andExpect(content().string(containsString("More in Peru")))
                .andExpect(content().string(containsString("Titicaca")));
    }

    @Test
    void testMissingAttractionReturnsNotFound() throws Exception {
        when(attractionService.getAttractionDetails(999L)).thenThrow(new AttractionNotFoundException(999L));

        mockMvc.perform(get("/attraction/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testAdminPageRedirectsAnonymousUserToLogin() throws Exception {
        // Browsers get a redirect to the login form, API clients get 401
        mockMvc.perform(get("/admin").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminPageForAdmin() throws Exception {
        when(attractionService.getStatistics()).thenReturn(Map.of(
                "totalAttractions", 2L,
                "visibleAttractions", 1L,
                "hiddenAttractions", 1L,
                "withoutPhoto", 0L,
                "totalImages", 2L,
                "countries", List.of("Brazil"),
                "countryCount", 1,
                "failedCountries", List.of("Venezuela"),
                "syncedCountryCount", 12
        ));
        AttractionDTO christ = new AttractionDTO(
                3L, "Christ the Redeemer", "monument", List.of("Brazil"), -22.95, -43.21,
                "http://commons.wikimedia.org/wiki/Special:FilePath/Christ.jpg", PhotoLayout.FIT);
        when(attractionService.findForAdmin(null, "Brazil", AdminFilter.HIDDEN))
                .thenReturn(List.of(new AdminAttractionDTO(christ, 92, true, 2667, 4000)));
        when(wikidataService.countryNames()).thenReturn(List.of("Brazil", "Peru"));

        mockMvc.perform(get("/admin").param("country", "Brazil").param("show", "HIDDEN"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"))
                .andExpect(content().string(containsString("1 failed last time")))
                .andExpect(content().string(containsString("Christ the Redeemer")))
                .andExpect(content().string(containsString("Whole photo")))
                .andExpect(content().string(containsString("2667 × 4000 px")))
                .andExpect(content().string(containsString("https://commons.wikimedia.org/wiki/File:Christ.jpg")))
                // a hidden place is not linked: its page is not found
                .andExpect(content().string(not(containsString("/attraction/3"))))
                .andExpect(content().string(containsString(">Show</button>")));
    }

    @Test
    void testLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void testWrongPasswordReturnsToLoginPage() throws Exception {
        mockMvc.perform(post("/login").with(csrf()).param("username", "admin").param("password", "wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }
}
