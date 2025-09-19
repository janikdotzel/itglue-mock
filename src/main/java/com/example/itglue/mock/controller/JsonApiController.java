package com.example.itglue.mock.controller;

import com.example.itglue.mock.model.JsonApiResource;
import com.example.itglue.mock.service.MockDataRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "/public_api/v1", produces = "application/vnd.api+json")
public class JsonApiController {

    private final MockDataRepository repository;
    private final ObjectMapper objectMapper;

    public JsonApiController(MockDataRepository repository) {
        this.repository = repository;
        this.objectMapper = repository.getObjectMapper();
    }

    @GetMapping(path = "/{resourceType}")
    public ResponseEntity<JsonNode> getCollection(
            @PathVariable("resourceType") @NotBlank String resourceType,
            @RequestParam Map<String, String> queryParams
    ) {
        Map<String, String> filters = extractFilters(queryParams);
        List<JsonApiResource> resources = repository.filter(resourceType, filters);
        List<String> sortFields = parseSortFields(queryParams.get("sort"));
        resources = repository.sort(resources, sortFields);
        int pageNumber = parseInt(queryParams.get("page[number]"), 1);
        int pageSize = parseInt(queryParams.get("page[size]"), 0);
        List<JsonApiResource> paged = pageSize > 0
                ? repository.paginate(resources, pageNumber, pageSize)
                : resources;

        ObjectNode response = objectMapper.createObjectNode();
        response.set("data", repository.toArrayNode(paged));
        attachIncluded(response, paged, queryParams.get("include"));
        response.set("meta", buildMeta(resources.size(), pageNumber, pageSize));
        response.set("links", buildCollectionLinks(resourceType, pageNumber, pageSize));
        return ResponseEntity.ok(response);
    }

    @GetMapping(path = "/{resourceType}/{id}")
    public ResponseEntity<JsonNode> getResource(
            @PathVariable("resourceType") String resourceType,
            @PathVariable("id") String id,
            @RequestParam Map<String, String> queryParams
    ) {
        Optional<JsonApiResource> resourceOptional = repository.findById(resourceType, id);
        if (resourceOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(errorResponse(HttpStatus.NOT_FOUND.value(), "Resource not found"));
        }
        JsonApiResource resource = resourceOptional.get();
        ObjectNode response = objectMapper.createObjectNode();
        response.set("data", repository.toObjectNode(resource));
        attachIncluded(response, List.of(resource), queryParams.get("include"));
        response.set("links", buildSelfLink(resourceType, id));
        return ResponseEntity.ok(response);
    }

    @GetMapping(path = "/{resourceType}/{id}/relationships/{relationship}")
    public ResponseEntity<JsonNode> getRelationship(
            @PathVariable String resourceType,
            @PathVariable String id,
            @PathVariable String relationship
    ) {
        Optional<JsonApiResource> resourceOptional = repository.findById(resourceType, id);
        if (resourceOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(errorResponse(HttpStatus.NOT_FOUND.value(), "Resource not found"));
        }
        JsonApiResource resource = resourceOptional.get();
        ObjectNode relationshipNode = repository.getRelationshipData(resource, relationship);
        if (relationshipNode == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(errorResponse(HttpStatus.NOT_FOUND.value(), "Relationship not found"));
        }
        ObjectNode response = objectMapper.createObjectNode();
        response.set("data", relationshipNode.get("data"));
        response.set("links", buildRelationshipLinks(resourceType, id, relationship));
        return ResponseEntity.ok(response);
    }

    @GetMapping(path = "/{resourceType}/{id}/{relationship}")
    public ResponseEntity<JsonNode> getRelatedResources(
            @PathVariable String resourceType,
            @PathVariable String id,
            @PathVariable String relationship
    ) {
        Optional<JsonApiResource> resourceOptional = repository.findById(resourceType, id);
        if (resourceOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(errorResponse(HttpStatus.NOT_FOUND.value(), "Resource not found"));
        }
        JsonApiResource resource = resourceOptional.get();
        List<JsonApiResource> related = repository.findRelated(resource, relationship);
        ObjectNode response = objectMapper.createObjectNode();
        response.set("data", repository.toArrayNode(related));
        response.set("links", buildRelationshipLinks(resourceType, id, relationship));
        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/{resourceType}", consumes = "application/vnd.api+json")
    public ResponseEntity<JsonNode> createResource(
            @PathVariable String resourceType,
            @RequestBody JsonNode payload,
            HttpServletResponse servletResponse
    ) throws IOException {
        JsonNode dataNode = payload.path("data");
        if (!dataNode.isObject()) {
            return ResponseEntity.badRequest()
                    .body(errorResponse(HttpStatus.BAD_REQUEST.value(), "Missing data object"));
        }
        String type = dataNode.path("type").asText();
        if (!resourceType.equals(type)) {
            return ResponseEntity.badRequest()
                    .body(errorResponse(HttpStatus.BAD_REQUEST.value(), "Resource type mismatch"));
        }
        ObjectNode attributes = dataNode.has("attributes") && dataNode.get("attributes").isObject()
                ? (ObjectNode) dataNode.get("attributes")
                : objectMapper.createObjectNode();
        ObjectNode relationships = dataNode.has("relationships") && dataNode.get("relationships").isObject()
                ? (ObjectNode) dataNode.get("relationships")
                : null;
        JsonApiResource created = repository.create(resourceType, attributes, relationships);
        ObjectNode response = objectMapper.createObjectNode();
        response.set("data", repository.toObjectNode(created));
        response.set("links", buildSelfLink(resourceType, created.getId()));
        servletResponse.setHeader("Location", String.format("/public_api/v1/%s/%s", resourceType, created.getId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping(path = "/{resourceType}/{id}", consumes = "application/vnd.api+json")
    public ResponseEntity<JsonNode> updateResource(
            @PathVariable String resourceType,
            @PathVariable String id,
            @RequestBody JsonNode payload
    ) {
        JsonNode dataNode = payload.path("data");
        if (!dataNode.isObject()) {
            return ResponseEntity.badRequest()
                    .body(errorResponse(HttpStatus.BAD_REQUEST.value(), "Missing data object"));
        }
        String type = dataNode.path("type").asText(resourceType);
        if (!resourceType.equals(type)) {
            return ResponseEntity.badRequest()
                    .body(errorResponse(HttpStatus.BAD_REQUEST.value(), "Resource type mismatch"));
        }
        ObjectNode attributes = dataNode.has("attributes") && dataNode.get("attributes").isObject()
                ? (ObjectNode) dataNode.get("attributes")
                : null;
        ObjectNode relationships = dataNode.has("relationships") && dataNode.get("relationships").isObject()
                ? (ObjectNode) dataNode.get("relationships")
                : null;
        try {
            JsonApiResource updated = repository.update(resourceType, id, attributes, relationships);
            ObjectNode response = objectMapper.createObjectNode();
            response.set("data", repository.toObjectNode(updated));
            response.set("links", buildSelfLink(resourceType, id));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(errorResponse(HttpStatus.NOT_FOUND.value(), e.getMessage()));
        }
    }

    @DeleteMapping(path = "/{resourceType}/{id}")
    public ResponseEntity<Void> deleteResource(
            @PathVariable String resourceType,
            @PathVariable String id
    ) {
        repository.delete(resourceType, id);
        return ResponseEntity.noContent().build();
    }

    private Map<String, String> extractFilters(Map<String, String> queryParams) {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("filter[") && key.endsWith("]")) {
                String attribute = key.substring(7, key.length() - 1);
                filters.put(attribute, entry.getValue());
            }
        }
        return filters;
    }

    private List<String> parseSortFields(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Collections.emptyList();
        }
        String[] parts = sortParam.split(",");
        List<String> fields = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                fields.add(part.trim());
            }
        }
        return fields;
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private ObjectNode buildMeta(int totalItems, int pageNumber, int pageSize) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("total", totalItems);
        if (pageSize > 0) {
            meta.put("page-number", pageNumber);
            meta.put("page-size", pageSize);
            meta.put("total-pages", (int) Math.ceil((double) totalItems / pageSize));
        }
        return meta;
    }

    private ObjectNode buildCollectionLinks(String resourceType, int pageNumber, int pageSize) {
        ObjectNode links = objectMapper.createObjectNode();
        links.put("self", buildPageLink(resourceType, pageNumber));
        if (pageSize > 0 && pageNumber > 1) {
            links.put("prev", buildPageLink(resourceType, pageNumber - 1));
        }
        if (pageSize > 0) {
            links.put("next", buildPageLink(resourceType, pageNumber + 1));
        }
        return links;
    }

    private ObjectNode buildSelfLink(String resourceType, String id) {
        ObjectNode links = objectMapper.createObjectNode();
        links.put("self", String.format("/public_api/v1/%s/%s", resourceType, id));
        return links;
    }

    private ObjectNode buildRelationshipLinks(String resourceType, String id, String relationship) {
        ObjectNode links = objectMapper.createObjectNode();
        links.put("self", String.format("/public_api/v1/%s/%s/relationships/%s", resourceType, id, relationship));
        links.put("related", String.format("/public_api/v1/%s/%s/%s", resourceType, id, relationship));
        return links;
    }

    private String buildPageLink(String resourceType, int pageNumber) {
        return String.format("/public_api/v1/%s?page[number]=%d", resourceType, pageNumber);
    }

    private void attachIncluded(ObjectNode response, List<JsonApiResource> resources, String includeParam) {
        if (includeParam == null || includeParam.isBlank()) {
            return;
        }
        Map<String, List<List<String>>> includePaths = parseInclude(includeParam);
        Set<String> visited = new LinkedHashSet<>();
        List<JsonApiResource> included = new ArrayList<>();
        for (JsonApiResource resource : resources) {
            collectIncludes(resource, includePaths, included, visited);
        }
        if (!included.isEmpty()) {
            ArrayNode includedNode = objectMapper.createArrayNode();
            for (JsonApiResource item : included) {
                includedNode.add(repository.toObjectNode(item));
            }
            response.set("included", includedNode);
        }
    }

    private Map<String, List<List<String>>> parseInclude(String includeParam) {
        Map<String, List<List<String>>> includes = new LinkedHashMap<>();
        String[] parts = includeParam.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] path = trimmed.split("\\.");
            String root = path[0];
            List<String> remainder = new ArrayList<>();
            for (int i = 1; i < path.length; i++) {
                remainder.add(path[i]);
            }
            includes.computeIfAbsent(root, key -> new ArrayList<>()).add(remainder);
        }
        return includes;
    }

    private void collectIncludes(
            JsonApiResource resource,
            Map<String, List<List<String>>> includePaths,
            List<JsonApiResource> included,
            Set<String> visited
    ) {
        for (Map.Entry<String, List<List<String>>> entry : includePaths.entrySet()) {
            String relationshipName = entry.getKey();
            List<JsonApiResource> related = repository.findRelated(resource, relationshipName);
            for (JsonApiResource relatedResource : related) {
                String key = relatedResource.getType() + ":" + relatedResource.getId();
                if (visited.add(key)) {
                    included.add(relatedResource);
                }
                List<List<String>> nested = entry.getValue().stream()
                        .filter(path -> !path.isEmpty())
                        .map(ArrayList::new)
                        .collect(Collectors.toList());
                if (!nested.isEmpty()) {
                    Map<String, List<List<String>>> nestedMap = new LinkedHashMap<>();
                    for (List<String> path : nested) {
                        String root = path.get(0);
                        List<String> remainder = path.size() > 1
                                ? new ArrayList<>(path.subList(1, path.size()))
                                : Collections.emptyList();
                        nestedMap.computeIfAbsent(root, key -> new ArrayList<>()).add(remainder);
                    }
                    collectIncludes(relatedResource, nestedMap, included, visited);
                }
            }
        }
    }

    private ObjectNode errorResponse(int status, String detail) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("status", status);
        error.put("detail", detail);
        ObjectNode response = objectMapper.createObjectNode();
        response.set("errors", objectMapper.createArrayNode().add(error));
        return response;
    }
}
