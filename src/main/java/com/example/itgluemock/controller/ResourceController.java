package com.example.itgluemock.controller;

import com.example.itgluemock.exception.ResourceNotFoundException;
import com.example.itgluemock.store.JsonApiDataStore;
import com.example.itgluemock.store.JsonApiDataStore.IncludeTree;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class ResourceController {

    private final JsonApiDataStore dataStore;
    private final ObjectMapper objectMapper;

    public ResourceController(JsonApiDataStore dataStore, ObjectMapper objectMapper) {
        this.dataStore = dataStore;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{resourceType:^(?!search$).+}")
    public ResponseEntity<JsonNode> listResources(
            @PathVariable String resourceType,
            @RequestParam(name = "page[size]", defaultValue = "25") int pageSize,
            @RequestParam(name = "page[number]", defaultValue = "1") int pageNumber,
            @RequestParam(name = "include", required = false) String includeParam) {
        validateResourceType(resourceType);
        IncludeTree includeTree = dataStore.parseIncludeParameter(includeParam);
        ObjectNode document =
                dataStore.buildCollectionDocument(resourceType, pageSize, pageNumber, includeTree);
        return ResponseEntity.ok(document);
    }

    @GetMapping("/{resourceType:^(?!search$).+}/{id}")
    public ResponseEntity<JsonNode> fetchResource(
            @PathVariable String resourceType,
            @PathVariable String id,
            @RequestParam(name = "include", required = false) String includeParam) {
        validateResourceType(resourceType);
        IncludeTree includeTree = dataStore.parseIncludeParameter(includeParam);
        return dataStore
                .buildResourceDocument(resourceType, id, includeTree)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException(resourceType + " " + id + " not found"));
    }

    @GetMapping("/{resourceType:^(?!search$).+}/{id}/relationships/{relationship}")
    public ResponseEntity<JsonNode> fetchRelationship(
            @PathVariable String resourceType,
            @PathVariable String id,
            @PathVariable String relationship) {
        validateResourceType(resourceType);
        return dataStore
                .buildRelationshipDocument(resourceType, id, relationship)
                .map(ResponseEntity::ok)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Relationship "
                                                + relationship
                                                + " for "
                                                + resourceType
                                                + " "
                                                + id
                                                + " not found"));
    }

    @GetMapping("/{resourceType:^(?!search$).+}/{id}/{relatedResource}")
    public ResponseEntity<JsonNode> fetchRelatedResources(
            @PathVariable String resourceType,
            @PathVariable String id,
            @PathVariable String relatedResource) {
        validateResourceType(resourceType);
        JsonNode relationshipData =
                dataStore
                        .buildRelationshipDocument(resourceType, id, relatedResource)
                        .map(doc -> doc.path("data"))
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Relationship "
                                                        + relatedResource
                                                        + " for "
                                                        + resourceType
                                                        + " "
                                                        + id
                                                        + " not found"));

        ObjectNode document = objectMapper.createObjectNode();
        if (relationshipData.isMissingNode() || relationshipData.isNull()) {
            document.putNull("data");
            return ResponseEntity.ok(document);
        }

        if (relationshipData.isArray()) {
            ArrayNode data = objectMapper.createArrayNode();
            for (JsonNode related : relationshipData) {
                String type = related.path("type").asText();
                String relatedId = related.path("id").asText();
                JsonNode resource =
                        dataStore
                                .findResource(type, relatedId)
                                .orElseThrow(
                                        () ->
                                                new ResourceNotFoundException(
                                                        type + " " + relatedId + " not found"));
                data.add(resource);
            }
            document.set("data", data);
        } else {
            String type = relationshipData.path("type").asText();
            String relatedId = relationshipData.path("id").asText();
            JsonNode resource =
                    dataStore
                            .findResource(type, relatedId)
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    type + " " + relatedId + " not found"));
            document.set("data", resource);
        }

        return ResponseEntity.ok(document);
    }

    private void validateResourceType(String resourceType) {
        if (!dataStore.hasResourceType(resourceType)) {
            throw new ResourceNotFoundException("Resource type " + resourceType + " not found");
        }
    }
}
