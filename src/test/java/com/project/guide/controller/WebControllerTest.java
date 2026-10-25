package com.project.guide.controller;

import com.project.guide.config.SecurityConfig;
import com.project.guide.dto.AttractionDTO;
import com.project.guide.dto.AttractionDetailDTO;
import com.project.guide.exception.AttractionNotFoundException;
import com.project.guide.service.AttractionService;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    private final AttractionDTO machuPicchu = new AttractionDTO(
            1L, "Machu Picchu", "World Heritage Site", List.of("Peru"), -13.1631, -72.5450, "http://example.com/image.jpg"
    );

    @Test
    void testHomePageListsAttractions() throws Exception {
        when(attractionService.getAllAttractions()).thenReturn(List.of(machuPicchu));
        when(attractionService.getAllCountries()).thenReturn(List.of("Peru"));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("attractions", List.of(machuPicchu)))
                .andExpect(content().string(containsString("Machu Picchu")));
    }

    @Test
    void testHomePageFiltersByCountry() throws Exception {
        when(attractionService.getAttractionsByCountry("Peru")).thenReturn(List.of(machuPicchu));
        when(attractionService.getAllCountries()).thenReturn(List.of("Chile", "Peru"));

        mockMvc.perform(get("/").param("country", "Peru"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedCountry", "Peru"));

        verify(attractionService, never()).getAllAttractions();
    }

    @Test
    void testHomePageSearchesByName() throws Exception {
        when(attractionService.searchAttractions("machu")).thenReturn(List.of(machuPicchu));
        when(attractionService.getAllCountries()).thenReturn(List.of("Peru"));

        mockMvc.perform(get("/").param("search", "machu"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("searchTerm", "machu"));
    }

    @Test
    void testAttractionDetailsPage() throws Exception {
        AttractionDetailDTO details = new AttractionDetailDTO(
                1L, "Machu Picchu", "World Heritage Site", List.of("Peru"), -13.1631, -72.5450,
                "http://example.com/image.jpg", "Inca citadel",
                "https://en.wikipedia.org/wiki/Machu_Picchu", "http://www.wikidata.org/entity/Q676203",
                List.of("http://example.com/image.jpg")
        );
        when(attractionService.getAttractionDetails(1L)).thenReturn(details);

        mockMvc.perform(get("/attraction/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("detail"))
                .andExpect(content().string(containsString("Inca citadel")));
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
    @WithMockUser(roles = "ADMIN")
    void testAdminPageForAdmin() throws Exception {
        when(attractionService.getStatistics()).thenReturn(Map.of(
                "totalAttractions", 1L,
                "totalImages", 2L,
                "countries", List.of("Peru"),
                "countryCount", 1
        ));

        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"));
    }
}
