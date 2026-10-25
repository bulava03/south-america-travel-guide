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
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AttractionApiController.class,
        properties = {"app.admin.username=admin", "app.admin.password=test"}
)
@Import(SecurityConfig.class)
class AttractionApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttractionService attractionService;

    private final AttractionDTO machuPicchu = new AttractionDTO(
            1L, "Machu Picchu", "World Heritage Site", List.of("Peru"), -13.1631, -72.5450, "http://example.com/image.jpg"
    );

    @Test
    void testGetAllAttractions() throws Exception {
        when(attractionService.getAllAttractions()).thenReturn(List.of(machuPicchu));

        mockMvc.perform(get("/api/attractions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Machu Picchu"))
                .andExpect(jsonPath("$[0].countries[0]").value("Peru"));
    }

    @Test
    void testGetAttractionsByCountry() throws Exception {
        when(attractionService.getAttractionsByCountry("Peru")).thenReturn(List.of(machuPicchu));

        mockMvc.perform(get("/api/attractions").param("country", "Peru"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].countries[0]").value("Peru"));

        verify(attractionService, never()).getAllAttractions();
    }

    @Test
    void testSearchTakesPrecedenceOverCountry() throws Exception {
        when(attractionService.searchAttractions("Machu")).thenReturn(List.of(machuPicchu));

        mockMvc.perform(get("/api/attractions").param("search", "Machu").param("country", "Chile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Machu Picchu"));

        verify(attractionService, never()).getAttractionsByCountry(any());
    }

    @Test
    void testGetAttractionDetails() throws Exception {
        AttractionDetailDTO details = new AttractionDetailDTO(
                1L, "Machu Picchu", "World Heritage Site", List.of("Peru"), -13.1631, -72.5450,
                "http://example.com/image.jpg", "Inca citadel",
                "https://en.wikipedia.org/wiki/Machu_Picchu", "http://www.wikidata.org/entity/Q676203",
                List.of("http://example.com/image.jpg")
        );
        when(attractionService.getAttractionDetails(1L)).thenReturn(details);

        mockMvc.perform(get("/api/attractions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Machu Picchu"))
                .andExpect(jsonPath("$.description").value("Inca citadel"))
                .andExpect(jsonPath("$.additionalImages.length()").value(1));
    }

    @Test
    void testGetAttractionDetailsNotFound() throws Exception {
        when(attractionService.getAttractionDetails(999L)).thenThrow(new AttractionNotFoundException(999L));

        mockMvc.perform(get("/api/attractions/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Attraction not found with id: 999"));
    }

    @Test
    void testGetCountries() throws Exception {
        when(attractionService.getAllCountries()).thenReturn(List.of("Argentina", "Chile", "Peru"));

        mockMvc.perform(get("/api/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2]").value("Peru"));
    }

    @Test
    void testAdminApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/stats").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
