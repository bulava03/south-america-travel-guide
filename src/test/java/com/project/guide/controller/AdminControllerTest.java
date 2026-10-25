package com.project.guide.controller;

import com.project.guide.config.SecurityConfig;
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

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AdminController.class,
        properties = {"app.admin.username=admin", "app.admin.password=test"}
)
@Import(SecurityConfig.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttractionService attractionService;

    @Test
    void testSyncRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/sync").with(csrf()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verify(attractionService, never()).syncWithWikidata();
    }

    @Test
    @WithMockUser(roles = "USER")
    void testSyncRequiresAdminRole() throws Exception {
        mockMvc.perform(post("/api/admin/sync").with(csrf()))
                .andExpect(status().isForbidden());

        verify(attractionService, never()).syncWithWikidata();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testSyncRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/admin/sync"))
                .andExpect(status().isForbidden());

        verify(attractionService, never()).syncWithWikidata();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testSyncSuccess() throws Exception {
        mockMvc.perform(post("/api/admin/sync").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        verify(attractionService).syncWithWikidata();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testSyncFailureReturnsError() throws Exception {
        doThrow(new RuntimeException("Failed to fetch attractions from Wikidata"))
                .when(attractionService).syncWithWikidata();

        mockMvc.perform(post("/api/admin/sync").with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void testStatsWithHttpBasicCredentials() throws Exception {
        when(attractionService.getStatistics()).thenReturn(Map.of(
                "totalAttractions", 2L,
                "totalImages", 5L,
                "countries", List.of("Argentina", "Brazil"),
                "countryCount", 2
        ));

        mockMvc.perform(get("/api/admin/stats").with(httpBasic("admin", "test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAttractions").value(2))
                .andExpect(jsonPath("$.countryCount").value(2));
    }
}
