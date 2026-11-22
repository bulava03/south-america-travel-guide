package com.project.guide.controller;

import com.project.guide.config.SecurityConfig;
import com.project.guide.dto.AttractionDTO;
import com.project.guide.dto.AttractionDetailDTO;
import com.project.guide.dto.PhotoLayout;
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

import static org.mockito.Mockito.when;
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

    @Test
    void testGetAttractionsWithFilters() throws Exception {
        when(attractionService.findAttractions("Peru", "Machu")).thenReturn(List.of(new AttractionDTO(
                1L, "Machu Picchu", "archaeological site", List.of("Peru"), -13.1631, -72.5450, "http://example.com/a.jpg", PhotoLayout.COVER
        )));

        mockMvc.perform(get("/api/attractions").param("country", "Peru").param("search", "Machu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Machu Picchu"))
                .andExpect(jsonPath("$[0].countries[0]").value("Peru"));
    }

    @Test
    void testGetAttractionDetails() throws Exception {
        when(attractionService.getAttractionDetails(1L)).thenReturn(new AttractionDetailDTO(
                new AttractionDTO(1L, "Machu Picchu", "archaeological site", List.of("Peru"), -13.1631, -72.5450,
                        "http://example.com/a.jpg", PhotoLayout.COVER),
                "Inca citadel", "https://en.wikipedia.org/wiki/Machu_Picchu", "http://www.wikidata.org/entity/Q676203"
        ));

        mockMvc.perform(get("/api/attractions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Machu Picchu"))
                .andExpect(jsonPath("$.card").doesNotExist())
                .andExpect(jsonPath("$.description").value("Inca citadel"))
                .andExpect(jsonPath("$.image").value("http://example.com/a.jpg"));
    }

    @Test
    void testGetAttractionDetailsNotFound() throws Exception {
        when(attractionService.getAttractionDetails(999L)).thenThrow(new AttractionNotFoundException(999L));

        mockMvc.perform(get("/api/attractions/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Attraction not found with id: 999"))
                .andExpect(jsonPath("$.instance").value("/api/attractions/999"));
    }

    @Test
    void testWrongIdTypeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/attractions/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
