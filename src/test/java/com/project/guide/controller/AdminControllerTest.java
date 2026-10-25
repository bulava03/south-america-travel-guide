package com.project.guide.controller;

import com.project.guide.config.SecurityConfig;
import com.project.guide.dto.SyncStatus;
import com.project.guide.exception.AttractionNotFoundException;
import com.project.guide.exception.SyncInProgressException;
import com.project.guide.service.AttractionService;
import com.project.guide.service.SyncService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @MockBean
    private SyncService syncService;

    @Test
    void testSyncRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/sync").with(csrf()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verify(syncService, never()).startAll();
    }

    @Test
    @WithMockUser(roles = "USER")
    void testSyncRequiresAdminRole() throws Exception {
        mockMvc.perform(post("/api/admin/sync").with(csrf()))
                .andExpect(status().isForbidden());

        verify(syncService, never()).startAll();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testSyncRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/admin/sync"))
                .andExpect(status().isForbidden());

        verify(syncService, never()).startAll();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testSyncStartsInBackground() throws Exception {
        when(syncService.status()).thenReturn(new SyncStatus(true, "WIKIDATA", null, null, null, null, List.of()));

        mockMvc.perform(post("/api/admin/sync").with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.running").value(true));

        verify(syncService).startAll();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testSyncOfOneCountry() throws Exception {
        mockMvc.perform(post("/api/admin/sync/Peru").with(csrf()))
                .andExpect(status().isAccepted());

        verify(syncService).startCountry("Peru");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testSyncWhileRunningReturnsConflict() throws Exception {
        doThrow(new SyncInProgressException()).when(syncService).startAll();

        mockMvc.perform(post("/api/admin/sync").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A sync is already running"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testSyncOfUnknownCountryReturnsBadRequest() throws Exception {
        doThrow(new IllegalArgumentException("Unknown country: Mexico")).when(syncService).startCountry("Mexico");

        mockMvc.perform(post("/api/admin/sync/Mexico").with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testHideAttraction() throws Exception {
        mockMvc.perform(patch("/api/admin/attractions/5").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\": true}"))
                .andExpect(status().isNoContent());

        verify(attractionService).setHidden(5L, true);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testHideMissingAttractionReturnsNotFound() throws Exception {
        doThrow(new AttractionNotFoundException(9L)).when(attractionService).setHidden(9L, false);

        mockMvc.perform(patch("/api/admin/attractions/9").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\": false}"))
                .andExpect(status().isNotFound());
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
