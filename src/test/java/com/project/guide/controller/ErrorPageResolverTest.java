package com.project.guide.controller;

import com.project.guide.config.SecurityConfig;
import com.project.guide.dto.AttractionDTO;
import com.project.guide.dto.PhotoLayout;
import com.project.guide.service.AttractionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc does not forward to /error, so a test controller renders the view of the resolver
 * with the real error.html.
 */
@WebMvcTest(
        controllers = ErrorPageResolverTest.ErrorTestController.class,
        properties = {"app.admin.username=admin", "app.admin.password=test"}
)
@Import({SecurityConfig.class, ErrorPageResolver.class, ErrorPageResolverTest.ErrorTestController.class})
class ErrorPageResolverTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttractionService attractionService;

    @RestController
    static class ErrorTestController {

        @Autowired
        private ErrorPageResolver resolver;

        @GetMapping("/test-error/{status}")
        ModelAndView error(@PathVariable int status, HttpServletRequest request) {
            return resolver.resolveErrorView(request, HttpStatus.valueOf(status),
                    Map.of("status", status, "path", "/attraction/1"));
        }
    }

    @Test
    void testNotFoundPageLinksToMostVisitedPlaces() throws Exception {
        when(attractionService.findMostVisited(anyInt())).thenReturn(List.of(new AttractionDTO(
                1L, "Machu Picchu", "archaeological site", List.of("Peru"), -13.1631, -72.5450, null, PhotoLayout.NONE
        )));

        mockMvc.perform(get("/test-error/404"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("This place is not on our map")))
                .andExpect(content().string(containsString("Most visited:")))
                .andExpect(content().string(containsString("href=\"/attraction/1\"")))
                .andExpect(content().string(containsString("Salar de Uyuni")));
    }

    @Test
    void testNotFoundPageWithoutDatabase() throws Exception {
        when(attractionService.findMostVisited(anyInt())).thenThrow(new IllegalStateException("database is down"));

        mockMvc.perform(get("/test-error/404"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("This place is not on our map")))
                .andExpect(content().string(not(containsString("Most visited:"))));
    }

    @Test
    void testServerErrorPageDoesNotTouchDatabase() throws Exception {
        mockMvc.perform(get("/test-error/500"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("Something went wrong on our side")))
                .andExpect(content().string(containsString("Try again")))
                .andExpect(content().string(containsString("Torres del Paine")));

        verify(attractionService, never()).findMostVisited(anyInt());
    }
}
