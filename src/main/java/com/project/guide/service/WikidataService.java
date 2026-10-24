package com.project.guide.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Service for querying Wikidata via SPARQL.
 * Fetches tourist attractions in South America.
 */
@Service
@Slf4j
public class WikidataService {
    
    @Value("${api.wikidata.endpoint}")
    private String wikidataEndpoint;
    
    @Value("${api.wikidata.timeout}")
    private int timeout;

    @Value("${api.user-agent}")
    private String userAgent;
    
    /**
     * Fetches all tourist attractions from Wikidata.
     * Returns grouped data by attraction URI to avoid duplicates.
     * 
     * @return Map where key is attraction URI and value is attraction data with all images
     */
    public Map<String, Map<String, Object>> fetchAttractions() {
        log.info("Starting to fetch attractions from Wikidata...");
        
        String sparqlQuery = buildSparqlQuery();
        
        // Wikimedia rejects requests with the default Java User-Agent (HTTP 403)
        try (QueryExecution qexec = QueryExecutionHTTP.service(wikidataEndpoint)
                .query(sparqlQuery)
                .httpHeader("User-Agent", userAgent)
                .timeout(timeout, TimeUnit.MILLISECONDS)
                .build()) {
            Map<String, Map<String, Object>> groupedAttractions = parseResults(qexec.execSelect());
            log.info("Successfully fetched {} attractions", groupedAttractions.size());
            return groupedAttractions;
        } catch (Exception e) {
            log.error("Error fetching data from Wikidata", e);
            throw new RuntimeException("Failed to fetch attractions from Wikidata", e);
        }
    }
    
    /**
     * Groups SPARQL result rows by attraction URI.
     * One attraction may come in several rows, one per image.
     */
    Map<String, Map<String, Object>> parseResults(ResultSet results) {
        Map<String, Map<String, Object>> groupedAttractions = new LinkedHashMap<>();
        
        while (results.hasNext()) {
            QuerySolution solution = results.next();
            
            String uri = getStringValue(solution, "item");
            String name = getStringValue(solution, "itemLabel");
            String type = getStringValue(solution, "typeLabel");
            String country = getStringValue(solution, "countryLabel");
            String imageUrl = getStringValue(solution, "image");
            String coords = getStringValue(solution, "coord");
            String wikipedia = getStringValue(solution, "article");
            
            // Label service returns the entity id (e.g. "Q12345") when there is no English label
            if (name == null || name.matches("Q\\d+")) {
                continue;
            }
            
            // Group by URI
            groupedAttractions.putIfAbsent(uri, new HashMap<>());
            Map<String, Object> attraction = groupedAttractions.get(uri);
            
            // Set basic fields (only once per URI)
            if (!attraction.containsKey("name")) {
                attraction.put("uri", uri);
                attraction.put("name", name);
                attraction.put("type", type);
                attraction.put("country", country);
                attraction.put("wikipedia", wikipedia);
                attraction.put("images", new ArrayList<String>());
                
                // Parse coordinates
                if (coords != null && coords.startsWith("Point(")) {
                    String[] latLon = parseCoordinates(coords);
                    if (latLon != null) {
                        attraction.put("longitude", latLon[0]);
                        attraction.put("latitude", latLon[1]);
                    }
                }
            }
            
            // Add image to list (avoid duplicates)
            if (imageUrl != null && !imageUrl.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<String> images = (List<String>) attraction.get("images");
                if (!images.contains(imageUrl)) {
                    images.add(imageUrl);
                }
            }
        }
        
        return groupedAttractions;
    }
    
    /**
     * Builds the SPARQL query for fetching tourist attractions.
     */
    private String buildSparqlQuery() {
        return """
        PREFIX wd: <http://www.wikidata.org/entity/>
        PREFIX wdt: <http://www.wikidata.org/prop/direct/>
        PREFIX schema: <http://schema.org/>
        PREFIX wikibase: <http://wikiba.se/ontology#>
        PREFIX bd: <http://www.bigdata.com/rdf#>
        
        SELECT ?item ?itemLabel ?typeLabel ?countryLabel ?coord ?image ?article
        WHERE {
          VALUES ?type {
            wd:Q9259       # World Heritage Site
            wd:Q570116     # Tourist Attraction
            wd:Q839954     # Archaeological Site
            wd:Q1081138    # Historic Site
          }
          
          VALUES ?country {
            wd:Q419 wd:Q155 wd:Q298 wd:Q750 wd:Q414 wd:Q736
          }
          
          ?item wdt:P31 ?type;
                wdt:P17 ?country;
                wdt:P625 ?coord;
                wdt:P18 ?image.
          
          # English Wikipedia article
          ?article schema:about ?item;
                   schema:isPartOf <https://en.wikipedia.org/>.
          
          # Label service is much faster than joining rdfs:label with language filters
          SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
        }
        LIMIT 100
        """;
    }
    
    /**
     * Safely extracts string value from QuerySolution.
     */
    private String getStringValue(QuerySolution solution, String varName) {
        if (solution.contains(varName)) {
            RDFNode node = solution.get(varName);
            if (node != null) {
                if (node.isLiteral()) {
                    return node.asLiteral().getString();
                } else {
                    return node.toString();
                }
            }
        }
        return null;
    }

    /**
     * Parses WKT coordinate string "Point(longitude latitude)" into array [longitude, latitude].
     */
    private String[] parseCoordinates(String coords) {
        try {
            // Remove "Point(" and ")"
            String cleaned = coords.replace("Point(", "").replace(")", "");
            String[] parts = cleaned.split(" ");
            if (parts.length == 2) {
                return parts; // [longitude, latitude]
            }
        } catch (Exception e) {
            log.warn("Failed to parse coordinates: {}", coords);
        }
        return null;
    }
}
