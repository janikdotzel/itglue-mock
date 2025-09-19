package com.example.itgluemock.controller;

import com.example.itgluemock.service.SearchService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<JsonNode> search(
            @RequestParam(name = "query") String query,
            @RequestParam(name = "page[size]", defaultValue = "25") int pageSize,
            @RequestParam(name = "page[number]", defaultValue = "1") int pageNumber,
            @RequestParam MultiValueMap<String, String> rawParams) {
        java.util.Set<String> resourceTypes = ResourceTypeParser.parse(rawParams.get("filter[resource_type]"));
        String organizationId = rawParams.getFirst("filter[organization_id]");
        JsonNode result =
                searchService.search(
                        query,
                        pageSize,
                        pageNumber,
                        resourceTypes,
                        java.util.Optional.ofNullable(organizationId));
        return ResponseEntity.ok(result);
    }

    private static class ResourceTypeParser {
        private ResourceTypeParser() {}

        static java.util.Set<String> parse(java.util.List<String> rawValues) {
            if (rawValues == null || rawValues.isEmpty()) {
                return java.util.Set.of();
            }
            java.util.Set<String> values = new java.util.LinkedHashSet<>();
            for (String value : rawValues) {
                if (value == null) {
                    continue;
                }
                for (String token : value.split(",")) {
                    String trimmed = token.trim();
                    if (!trimmed.isEmpty()) {
                        values.add(trimmed);
                    }
                }
            }
            return values;
        }
    }
}
