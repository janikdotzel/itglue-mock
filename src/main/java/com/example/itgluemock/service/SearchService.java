package com.example.itgluemock.service;

import com.example.itgluemock.exception.BadRequestException;
import com.example.itgluemock.exception.ResourceNotFoundException;
import com.example.itgluemock.store.JsonApiDataStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private final JsonApiDataStore dataStore;
    private final ObjectMapper objectMapper;

    public SearchService(JsonApiDataStore dataStore, ObjectMapper objectMapper) {
        this.dataStore = dataStore;
        this.objectMapper = objectMapper;
    }

    public ObjectNode search(
            String query,
            int pageSize,
            int pageNumber,
            Set<String> resourceTypes,
            Optional<String> organizationIdFilter) {
        if (query == null || query.isBlank()) {
            throw new BadRequestException("query parameter is required");
        }

        Set<String> normalizedTypes = normalizeResourceTypes(resourceTypes);
        String normalizedQuery = query.toLowerCase(Locale.ROOT);

        List<JsonNode> candidates = selectCandidates(normalizedTypes);

        List<JsonNode> filtered = candidates.stream()
                .filter(node -> matchesQuery(node, normalizedQuery))
                .filter(node -> matchesOrganizationFilter(node, organizationIdFilter))
                .sorted(compareByDisplayName())
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        int validatedPageSize = pageSize <= 0 ? 25 : pageSize;
        int validatedPageNumber = Math.max(pageNumber, 1);
        int total = filtered.size();
        int fromIndex = Math.min((validatedPageNumber - 1) * validatedPageSize, total);
        int toIndex = Math.min(fromIndex + validatedPageSize, total);
        List<JsonNode> page = filtered.subList(fromIndex, toIndex);

        ArrayNode data = objectMapper.createArrayNode();
        page.stream().map(JsonNode::deepCopy).forEach(data::add);

        ObjectNode document = objectMapper.createObjectNode();
        document.set("data", data);
        document.set("meta", buildMeta(total, validatedPageSize, validatedPageNumber));
        document.set(
                "links",
                buildLinks(query, validatedPageSize, validatedPageNumber, total, normalizedTypes));
        return document;
    }

    private List<JsonNode> selectCandidates(Set<String> resourceTypes) {
        if (resourceTypes.isEmpty()) {
            return dataStore.resourceTypes().stream()
                    .flatMap(type -> dataStore.findResources(type).stream())
                    .map(JsonNode::deepCopy)
                    .toList();
        }
        List<JsonNode> results = new ArrayList<>();
        for (String type : resourceTypes) {
            if (!dataStore.hasResourceType(type)) {
                throw new ResourceNotFoundException("Resource type " + type + " not found");
            }
            results.addAll(dataStore.findResources(type));
        }
        return results;
    }

    private Set<String> normalizeResourceTypes(Set<String> resourceTypes) {
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String type : resourceTypes) {
            if (type == null) {
                continue;
            }
            String trimmed = type.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private boolean matchesQuery(JsonNode node, String query) {
        if (node.path("id").asText().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        JsonNode attributes = node.path("attributes");
        return searchAttributes(attributes, query);
    }

    private boolean searchAttributes(JsonNode node, String query) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isValueNode()) {
            return node.asText("").toLowerCase(Locale.ROOT).contains(query);
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                if (searchAttributes(element, query)) {
                    return true;
                }
            }
            return false;
        }
        if (node.isObject()) {
            for (JsonNode value : node) {
                if (searchAttributes(value, query)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesOrganizationFilter(JsonNode node, Optional<String> organizationId) {
        if (organizationId.isEmpty()) {
            return true;
        }
        JsonNode relationship = node.path("relationships").path("organization").path("data");
        return organizationId
                .map(id -> id.equals(relationship.path("id").asText()))
                .orElse(true);
    }

    private Comparator<JsonNode> compareByDisplayName() {
        return Comparator.comparing(
                node -> node.path("attributes").path("name").asText(node.path("id").asText()),
                String.CASE_INSENSITIVE_ORDER);
    }

    private ObjectNode buildMeta(int total, int pageSize, int pageNumber) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("total-count", total);
        meta.put("page-size", pageSize);
        meta.put("page-number", pageNumber);
        int pageCount = pageSize == 0 ? 1 : (int) Math.ceil(total / (double) pageSize);
        meta.put("page-count", Math.max(pageCount, 1));
        return meta;
    }

    private ObjectNode buildLinks(
            String query,
            int pageSize,
            int pageNumber,
            int total,
            Set<String> resourceTypes) {
        ObjectNode links = objectMapper.createObjectNode();
        String base = new StringBuilder("/search?query=")
                .append(query)
                .append("&page[size]=")
                .append(pageSize)
                .toString();
        if (!resourceTypes.isEmpty()) {
            String joined = resourceTypes.stream().collect(Collectors.joining(","));
            base += "&filter[resource_type]=" + joined;
        }
        links.put("self", base + "&page[number]=" + pageNumber);
        links.put("first", base + "&page[number]=1");
        int pageCount = pageSize == 0 ? 1 : (int) Math.ceil(total / (double) pageSize);
        links.put("last", base + "&page[number]=" + Math.max(pageCount, 1));
        if (pageNumber > 1) {
            links.put("prev", base + "&page[number]=" + (pageNumber - 1));
        }
        if (pageNumber < pageCount) {
            links.put("next", base + "&page[number]=" + (pageNumber + 1));
        }
        return links;
    }
}
