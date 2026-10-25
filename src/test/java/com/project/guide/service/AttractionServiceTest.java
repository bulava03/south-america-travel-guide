package com.project.guide.service;

import com.project.guide.dto.AdminAttractionDTO;
import com.project.guide.dto.AdminFilter;
import com.project.guide.dto.AttractionDTO;
import com.project.guide.dto.AttractionDetailDTO;
import com.project.guide.dto.SyncSummary;
import com.project.guide.dto.WikidataAttraction;
import com.project.guide.dto.WikidataFetchResult;
import com.project.guide.exception.AttractionNotFoundException;
import com.project.guide.model.Attraction;
import com.project.guide.model.AttractionImage;
import com.project.guide.model.CountrySync;
import com.project.guide.repository.AttractionRepository;
import com.project.guide.repository.AttractionImageRepository;
import com.project.guide.repository.CountrySyncRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttractionServiceTest {

    @Mock
    private AttractionRepository attractionRepository;

    @Mock
    private AttractionImageRepository imageRepository;

    @Mock
    private CountrySyncRepository countrySyncRepository;

    @Mock
    private WikipediaService wikipediaService;

    @InjectMocks
    private AttractionService attractionService;

    private Attraction testAttraction;

    @BeforeEach
    void setUp() {
        testAttraction = Attraction.builder()
                .id(1L)
                .wikidataUri("http://www.wikidata.org/entity/Q28572")
                .name("Machu Picchu")
                .type("World Heritage Site")
                .countries(new TreeSet<>(Set.of("Peru")))
                .popularity(140)
                .latitude(-13.1631)
                .longitude(-72.5450)
                .mainImage("http://example.com/image.jpg")
                .images(new ArrayList<>())
                .build();
    }

    @Test
    void testFindAttractionsSearchTakesPrecedenceOverCountry() {
        when(attractionRepository.searchByName("Machu")).thenReturn(List.of(testAttraction));

        List<AttractionDTO> result = attractionService.findAttractions("Chile", "Machu");

        assertEquals(1, result.size());
        assertEquals("Machu Picchu", result.get(0).getName());
        verify(attractionRepository, never()).findByCountry(any());
    }

    @Test
    void testFindAttractionsByCountryOrAll() {
        when(attractionRepository.findByCountry("Peru")).thenReturn(List.of(testAttraction));
        when(attractionRepository.findByHiddenFalseOrderByPopularityDesc()).thenReturn(List.of());

        assertEquals(List.of("Peru"), attractionService.findAttractions("Peru", "").get(0).getCountries());
        assertEquals(List.of(), attractionService.findAttractions(null, null));
    }

    @Test
    void testFindMostVisited() {
        when(attractionRepository.findByHiddenFalseOrderByPopularityDesc(Limit.of(4))).thenReturn(List.of(testAttraction));

        assertEquals("Machu Picchu", attractionService.findMostVisited(4).get(0).getName());
    }

    @Test
    void testGetAttractionDetailsUsesWikipediaDescription() {
        testAttraction.setWikipediaUrl("https://en.wikipedia.org/wiki/Machu_Picchu");
        when(attractionRepository.findById(1L)).thenReturn(Optional.of(testAttraction));
        when(wikipediaService.fetchWikipediaExtract("https://en.wikipedia.org/wiki/Machu_Picchu"))
                .thenReturn("Machu Picchu is a 15th-century Inca citadel.");

        var result = attractionService.getAttractionDetails(1L);

        assertEquals("Machu Picchu", result.getName());
        assertEquals(List.of("Peru"), result.getCountries());
        assertEquals("Machu Picchu is a 15th-century Inca citadel.", result.getDescription());
    }

    @Test
    void testGetAttractionDetailsNotFound() {
        when(attractionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(AttractionNotFoundException.class, () -> attractionService.getAttractionDetails(999L));
    }

    @Test
    void testHiddenAttractionIsNotFound() {
        testAttraction.setHidden(true);
        when(attractionRepository.findById(1L)).thenReturn(Optional.of(testAttraction));

        assertThrows(AttractionNotFoundException.class, () -> attractionService.getAttractionDetails(1L));
    }

    @Test
    void testSetHidden() {
        when(attractionRepository.findById(1L)).thenReturn(Optional.of(testAttraction));

        attractionService.setHidden(1L, true);

        assertTrue(testAttraction.isHidden());
    }

    @Test
    void testFindForAdminIncludesHiddenAndCombinesFilters() {
        Attraction hidden = attraction(2L, "Q2", "Colón Theater", Set.of("Argentina"));
        hidden.setHidden(true);
        Attraction visible = attraction(3L, "Q3", "Perito Moreno Glacier", Set.of("Argentina", "Chile"));
        when(attractionRepository.findAllByOrderByPopularityDesc()).thenReturn(List.of(testAttraction, hidden, visible));

        assertEquals(List.of("Colón Theater"), names(attractionService.findForAdmin(null, "Argentina", AdminFilter.HIDDEN)));
        assertEquals(List.of("Perito Moreno Glacier"), names(attractionService.findForAdmin(" moreno", null, AdminFilter.ALL)));
        assertEquals(3, attractionService.findForAdmin("", "", AdminFilter.ALL).size());
    }

    @Test
    void testFindMoreInCountryExcludesTheAttractionItself() {
        Attraction titicaca = Attraction.builder().id(2L).name("Titicaca")
                .countries(new TreeSet<>(Set.of("Bolivia", "Peru"))).images(new ArrayList<>()).build();
        when(attractionRepository.findByCountry("Peru")).thenReturn(List.of(testAttraction, titicaca));

        var machuPicchu = AttractionDetailDTO.from(testAttraction);
        List<AttractionDTO> more = attractionService.findMoreInCountry(machuPicchu, 3);

        assertEquals(List.of("Titicaca"), more.stream().map(AttractionDTO::getName).toList());
    }

    @Test
    void testSaveCreatesNewAttraction() {
        // Arrange
        WikidataAttraction iguazu = wikidataAttraction("http://www.wikidata.org/entity/Q36332", "Iguazu Falls",
                List.of("http://example.com/a.jpg", "http://example.com/b.jpg"));
        iguazu.getCountries().addAll(List.of("Argentina", "Brazil"));
        when(attractionRepository.findByWikidataUri(any())).thenReturn(Optional.empty());
        when(attractionRepository.findAll()).thenReturn(List.of());

        // Act
        SyncSummary summary = attractionService.saveSyncResult(
                result(List.of(iguazu), Map.of("Argentina", 1, "Brazil", 1), Map.of()));

        // Assert
        ArgumentCaptor<Attraction> saved = ArgumentCaptor.forClass(Attraction.class);
        verify(attractionRepository).save(saved.capture());
        Attraction attraction = saved.getValue();

        assertEquals(new SyncSummary(1, 1, 0), summary);
        assertNull(attraction.getId());
        assertEquals("http://www.wikidata.org/entity/Q36332", attraction.getWikidataUri());
        assertEquals("Iguazu Falls", attraction.getName());
        assertEquals(Set.of("Argentina", "Brazil"), attraction.getCountries());
        assertEquals(99, attraction.getPopularity());
        assertEquals(-25.695, attraction.getLatitude());
        assertEquals(-54.4367, attraction.getLongitude());
        assertEquals("http://example.com/a.jpg", attraction.getMainImage());
        assertEquals(1280, attraction.getMainImageWidth());
        assertEquals(789, attraction.getMainImageHeight());
        assertEquals(2, attraction.getImages().size());
        assertSame(attraction, attraction.getImages().get(0).getAttraction());
    }

    @Test
    void testSaveUpdatesExistingAttractionInsteadOfRecreating() {
        // Arrange
        testAttraction.addImage(AttractionImage.builder().imageUrl("http://example.com/old.jpg").build());
        WikidataAttraction updated = wikidataAttraction(testAttraction.getWikidataUri(), "Machu Picchu (updated)",
                List.of("http://example.com/new.jpg"));
        updated.getCountries().add("Peru");
        when(attractionRepository.findByWikidataUri(testAttraction.getWikidataUri()))
                .thenReturn(Optional.of(testAttraction));
        when(attractionRepository.findAll()).thenReturn(List.of(testAttraction));

        // Act
        SyncSummary summary = attractionService.saveSyncResult(result(List.of(updated), Map.of("Peru", 1), Map.of()));

        // Assert
        verify(attractionRepository).save(testAttraction);
        assertEquals(new SyncSummary(1, 0, 0), summary);
        assertEquals(1L, testAttraction.getId());
        assertEquals("Machu Picchu (updated)", testAttraction.getName());
        assertEquals(Set.of("Peru"), testAttraction.getCountries());
        assertEquals(1, testAttraction.getImages().size());
        assertEquals("http://example.com/new.jpg", testAttraction.getImages().get(0).getImageUrl());
        assertEquals("http://example.com/new.jpg", testAttraction.getMainImage());

        verify(attractionRepository, never()).deleteAll();
        verify(attractionRepository).deleteAll(List.of());
    }

    @Test
    void testSaveRemovesAttractionsMissingInWikidata() {
        // Arrange
        Attraction outdated = attraction(2L, "Q1", "Outdated", Set.of("Peru"));
        WikidataAttraction current = wikidataAttraction(testAttraction.getWikidataUri(), "Machu Picchu", List.of());
        when(attractionRepository.findByWikidataUri(testAttraction.getWikidataUri()))
                .thenReturn(Optional.of(testAttraction));
        when(attractionRepository.findAll()).thenReturn(List.of(testAttraction, outdated));

        // Act
        SyncSummary summary = attractionService.saveSyncResult(result(List.of(current), Map.of("Peru", 1), Map.of()));

        // Assert
        verify(attractionRepository).deleteAll(List.of(outdated));
        assertEquals(1, summary.removed());
    }

    @Test
    void testSaveKeepsAttractionsOfFailedCountries() {
        // Arrange: Chile query failed, so Chilean attractions are missing in the result
        Attraction chilean = attraction(2L, "Q2", "Torres del Paine National Park", Set.of("Chile"));
        WikidataAttraction current = wikidataAttraction(testAttraction.getWikidataUri(), "Machu Picchu", List.of());
        when(attractionRepository.findByWikidataUri(testAttraction.getWikidataUri()))
                .thenReturn(Optional.of(testAttraction));
        when(attractionRepository.findAll()).thenReturn(List.of(testAttraction, chilean));

        // Act
        attractionService.saveSyncResult(result(List.of(current), Map.of("Peru", 1), Map.of("Chile", "timed out")));

        // Assert
        verify(attractionRepository).save(testAttraction);
        verify(attractionRepository).deleteAll(List.of());
    }

    @Test
    void testSaveOfOneCountryKeepsAttractionsSharedWithOtherCountries() {
        // Arrange: only Brazil is synced and Iguazu Falls is no longer selected for it
        Attraction iguazu = attraction(2L, "Q36332", "Iguazu Falls", Set.of("Argentina", "Brazil"));
        Attraction brazilian = attraction(3L, "Q3", "Outdated", Set.of("Brazil"));
        when(attractionRepository.findAll()).thenReturn(List.of(iguazu, brazilian));

        // Act
        attractionService.saveSyncResult(result(List.of(), Map.of("Brazil", 0), Map.of()));

        // Assert
        verify(attractionRepository).deleteAll(List.of(brazilian));
    }

    @Test
    void testSaveKeepsHiddenAttractions() {
        // Arrange: hidden attractions are skipped by Wikidata queries, so they are never in the result
        Attraction hidden = attraction(2L, "Q2", "Hidden", Set.of("Peru"));
        hidden.setHidden(true);
        when(attractionRepository.findAll()).thenReturn(List.of(hidden));

        // Act
        attractionService.saveSyncResult(result(List.of(), Map.of("Peru", 0), Map.of()));

        // Assert
        verify(attractionRepository).deleteAll(List.of());
    }

    @Test
    void testSaveRecordsResultOfEveryCountry() {
        when(attractionRepository.findAll()).thenReturn(List.of());

        attractionService.saveSyncResult(result(List.of(), Map.of("Peru", 25), Map.of("Chile", "timed out")));

        ArgumentCaptor<List<CountrySync>> saved = ArgumentCaptor.captor();
        verify(countrySyncRepository).saveAll(saved.capture());
        Map<String, CountrySync> byCountry = saved.getValue().stream()
                .collect(java.util.stream.Collectors.toMap(CountrySync::getCountry, sync -> sync));
        assertTrue(byCountry.get("Peru").isSucceeded());
        assertEquals(25, byCountry.get("Peru").getPlaceCount());
        assertFalse(byCountry.get("Chile").isSucceeded());
        assertEquals("timed out", byCountry.get("Chile").getMessage());
    }

    @Test
    void testSaveChangesNothingWhenAllCountriesFailed() {
        SyncSummary summary = attractionService.saveSyncResult(result(List.of(), Map.of(), Map.of("Peru", "timed out")));

        assertEquals(new SyncSummary(0, 0, 0), summary);
        verify(countrySyncRepository).saveAll(any());
        verify(attractionRepository, never()).save(any());
        verify(attractionRepository, never()).deleteAll();
        verify(attractionRepository, never()).deleteAll(any());
    }

    private static WikidataFetchResult result(List<WikidataAttraction> attractions, Map<String, Integer> fetched,
                                              Map<String, String> failed) {
        return new WikidataFetchResult(attractions, fetched, failed);
    }

    private static Attraction attraction(Long id, String entityId, String name, Set<String> countries) {
        return Attraction.builder()
                .id(id)
                .wikidataUri("http://www.wikidata.org/entity/" + entityId)
                .name(name)
                .countries(new TreeSet<>(countries))
                .images(new ArrayList<>())
                .build();
    }

    private static List<String> names(List<AdminAttractionDTO> rows) {
        return rows.stream().map(row -> row.getCard().getName()).toList();
    }

    private WikidataAttraction wikidataAttraction(String uri, String name, List<String> images) {
        WikidataAttraction attraction = new WikidataAttraction();
        attraction.setUri(uri);
        attraction.setName(name);
        attraction.setType("waterfall");
        attraction.setWikipediaUrl("https://en.wikipedia.org/wiki/Iguazu_Falls");
        attraction.setLatitude(-25.695);
        attraction.setLongitude(-54.4367);
        attraction.setPopularity(99);
        attraction.getImages().addAll(images);
        attraction.setImageWidth(1280);
        attraction.setImageHeight(789);
        return attraction;
    }
}
