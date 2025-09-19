package com.example.itgluemock.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads JSON:API resource documents from {@code classpath:data/*.json} files and exposes utility
 * methods to build JSON:API compliant responses.
 */
@Component
public class JsonApiDataStore {

    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, ObjectNode>> resourcesByType = new LinkedHashMap<>();

    public JsonApiDataStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        loadDataFiles();
    }

    public boolean hasResourceType(String resourceType) {
        return resourcesByType.containsKey(resourceType);
    }

    public Set<String> resourceTypes() {
        return Collections.unmodifiableSet(resourcesByType.keySet());
    }

    public ObjectNode buildCollectionDocument(
            String resourceType, int pageSize, int pageNumber, IncludeTree includeTree) {
        List<ObjectNode> resources = new ArrayList<>(
                resourcesByType.getOrDefault(resourceType, Collections.emptyMap()).values());
        int total = resources.size();
        if (total == 0) {
            return emptyDocument();
        }

        int validatedPageSize = pageSize <= 0 ? total : pageSize;
        int validatedPageNumber = Math.max(pageNumber, 1);
        int fromIndex = Math.min((validatedPageNumber - 1) * validatedPageSize, total);
        int toIndex = Math.min(fromIndex + validatedPageSize, total);
        List<ObjectNode> page = resources.subList(fromIndex, toIndex);

        ArrayNode data = objectMapper.createArrayNode();
        page.stream().map(ObjectNode::deepCopy).forEach(data::add);

        ObjectNode document = objectMapper.createObjectNode();
        document.set("data", data);
        document.set("meta", buildMeta(total, validatedPageSize, validatedPageNumber));
        document.set(
                "links",
                buildPaginationLinks(
                        resourceType, total, validatedPageSize, validatedPageNumber));

        ArrayNode included = collectIncludedResources(page, includeTree);
        if (included.size() > 0) {
            document.set("included", included);
        }
        return document;
    }

    public Optional<ObjectNode> buildResourceDocument(
            String resourceType, String id, IncludeTree includeTree) {
        return Optional.ofNullable(resourcesByType.getOrDefault(resourceType, Map.of()).get(id))
                .map(ObjectNode::deepCopy)
                .map(resource -> enrichResourceDocument(resource, includeTree));
    }

    public Optional<ObjectNode> buildRelationshipDocument(
            String resourceType, String id, String relationshipName) {
        ObjectNode resource = resourcesByType.getOrDefault(resourceType, Map.of()).get(id);
        if (resource == null) {
            return Optional.empty();
        }

        JsonNode relationships = resource.path("relationships");
        if (!relationships.has(relationshipName)) {
            return Optional.empty();
        }

        JsonNode relationshipNode = relationships.path(relationshipName).path("data");
        ObjectNode document = objectMapper.createObjectNode();
        document.set("data", relationshipNode.deepCopy());
        return Optional.of(document);
    }

    public List<ObjectNode> findResources(String resourceType) {
        return new ArrayList<>(
                resourcesByType.getOrDefault(resourceType, Collections.emptyMap()).values());
    }

    public Optional<ObjectNode> findResource(String resourceType, String id) {
        return Optional.ofNullable(resourcesByType.getOrDefault(resourceType, Map.of()).get(id))
                .map(ObjectNode::deepCopy);
    }

    public IncludeTree parseIncludeParameter(String includeParam) {
        if (includeParam == null || includeParam.isBlank()) {
            return IncludeTree.empty();
        }
        String[] paths = includeParam.split(",");
        IncludeTree root = IncludeTree.empty();
        for (String path : paths) {
            String trimmed = path.trim();
            if (!trimmed.isEmpty()) {
                root = root.merge(IncludeTree.fromPath(trimmed));
            }
        }
        return root;
    }

    private ObjectNode enrichResourceDocument(ObjectNode resource, IncludeTree includeTree) {
        ObjectNode document = objectMapper.createObjectNode();
        document.set("data", resource);
        ArrayNode included = collectIncludedResources(List.of(resource), includeTree);
        if (included.size() > 0) {
            document.set("included", included);
        }
        return document;
    }

    private ArrayNode collectIncludedResources(List<ObjectNode> resources, IncludeTree includeTree) {
        ArrayNode included = objectMapper.createArrayNode();
        if (includeTree.isEmpty()) {
            return included;
        }

        Set<String> visited = new HashSet<>();
        Deque<IncludeRequest> queue = new ArrayDeque<>();
        for (ObjectNode resource : resources) {
            queue.add(new IncludeRequest(resource, includeTree));
        }

        while (!queue.isEmpty()) {
            IncludeRequest request = queue.pollFirst();
            ObjectNode relationships = request.resource().with("relationships");
            for (Map.Entry<String, IncludeTree> entry : request.includeTree().children().entrySet()) {
                String relationshipName = entry.getKey();
                JsonNode relationship = relationships.path(relationshipName).path("data");
                if (relationship.isMissingNode() || relationship.isNull()) {
                    continue;
                }

                if (relationship.isArray()) {
                    for (JsonNode node : relationship) {
                        addIncludedResource(
                                node.path("type").asText(),
                                node.path("id").asText(),
                                entry.getValue(),
                                visited,
                                included,
                                queue);
                    }
                } else {
                    addIncludedResource(
                            relationship.path("type").asText(),
                            relationship.path("id").asText(),
                            entry.getValue(),
                            visited,
                            included,
                            queue);
                }
            }
        }

        return included;
    }

    private void addIncludedResource(
            String type,
            String id,
            IncludeTree nestedIncludes,
            Set<String> visited,
            ArrayNode included,
            Deque<IncludeRequest> queue) {
        if (type == null || id == null || type.isEmpty() || id.isEmpty()) {
            return;
        }
        String key = type + ":" + id;
        if (visited.contains(key)) {
            return;
        }
        ObjectNode resource =
                resourcesByType.getOrDefault(type, Collections.emptyMap()).get(id);
        if (resource == null) {
            return;
        }
        visited.add(key);
        ObjectNode copy = resource.deepCopy();
        included.add(copy);
        if (!nestedIncludes.isEmpty()) {
            queue.add(new IncludeRequest(copy, nestedIncludes));
        }
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

    private ObjectNode buildPaginationLinks(
            String resourceType, int total, int pageSize, int pageNumber) {
        ObjectNode links = objectMapper.createObjectNode();
        String base = "/" + resourceType + "?page[size]=" + pageSize;
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

    private ObjectNode emptyDocument() {
        ObjectNode document = objectMapper.createObjectNode();
        document.set("data", objectMapper.createArrayNode());
        return document;
    }

    private void loadDataFiles() {
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver(getClass().getClassLoader());
        try {
            Resource[] resources = resolver.getResources("classpath:data/*.json");
            for (Resource resource : resources) {
                loadResourceFile(resource);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load mock data files", ex);
        }
    }

    private void loadResourceFile(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode document = objectMapper.readTree(inputStream);
            if (document == null || !document.has("data")) {
                return;
            }
            registerNodes(document.path("data"));
            registerNodes(document.path("included"));
        }
    }

    private void registerNodes(JsonNode nodes) {
        if (nodes == null || !nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            if (node instanceof ObjectNode objectNode) {
                String type = objectNode.path("type").asText(null);
                String id = objectNode.path("id").asText(null);
                if (type == null || id == null) {
                    continue;
                }
                resourcesByType
                        .computeIfAbsent(type, key -> new LinkedHashMap<>())
                        .put(id, objectNode);
            }
        }
    }

    public record IncludeTree(Map<String, IncludeTree> children) {

        public static IncludeTree empty() {
            return new IncludeTree(Map.of());
        }

        public static IncludeTree fromPath(String path) {
            String[] segments = path.split("\\.");
            return buildTree(segments, 0);
        }

        private static IncludeTree buildTree(String[] segments, int index) {
            if (index >= segments.length) {
                return empty();
            }
            String segment = segments[index];
            return new IncludeTree(
                    Map.of(segment, buildTree(segments, index + 1)));
        }

        public IncludeTree merge(IncludeTree other) {
            if (this.isEmpty()) {
                return other;
            }
            if (other.isEmpty()) {
                return this;
            }
            Map<String, IncludeTree> merged = new HashMap<>(this.children);
            for (Map.Entry<String, IncludeTree> entry : other.children.entrySet()) {
                merged.merge(entry.getKey(), entry.getValue(), IncludeTree::merge);
            }
            return new IncludeTree(Collections.unmodifiableMap(merged));
        }

        public boolean isEmpty() {
            return children.isEmpty();
        }
    }

    private record IncludeRequest(ObjectNode resource, IncludeTree includeTree) {}
}
