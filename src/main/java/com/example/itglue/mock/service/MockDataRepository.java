package com.example.itglue.mock.service;

import com.example.itglue.mock.model.JsonApiResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Component
public class MockDataRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockDataRepository.class);
    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, JsonApiResource>> storage = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public MockDataRepository(ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
        loadResources();
    }

    private void loadResources() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:mock-data/*.json");
        for (Resource resource : resources) {
            try (InputStream stream = resource.getInputStream()) {
                JsonNode node = objectMapper.readTree(stream);
                ArrayNode dataNode = (ArrayNode) node.path("data");
                for (JsonNode item : dataNode) {
                    JsonApiResource resourceObject = toResource(item);
                    storage.computeIfAbsent(resourceObject.getType(), key -> new LinkedHashMap<>())
                            .put(resourceObject.getId(), resourceObject);
                }
            }
        }
        LOGGER.info("Loaded {} resource types", storage.size());
    }

    private JsonApiResource toResource(JsonNode item) {
        String id = item.path("id").asText();
        String type = item.path("type").asText();
        ObjectNode attributes = item.hasNonNull("attributes") && item.get("attributes").isObject()
                ? (ObjectNode) item.get("attributes")
                : objectMapper.createObjectNode();
        ObjectNode relationships = item.hasNonNull("relationships") && item.get("relationships").isObject()
                ? (ObjectNode) item.get("relationships")
                : null;
        return new JsonApiResource(id, type, attributes, relationships);
    }

    public List<JsonApiResource> findAll(String type) {
        lock.readLock().lock();
        try {
            Map<String, JsonApiResource> map = storage.get(type);
            if (map == null) {
                return Collections.emptyList();
            }
            return new ArrayList<>(map.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<JsonApiResource> findById(String type, String id) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(storage.getOrDefault(type, Collections.emptyMap()).get(id));
        } finally {
            lock.readLock().unlock();
        }
    }

    public JsonApiResource create(String type, ObjectNode attributes, ObjectNode relationships) {
        lock.writeLock().lock();
        try {
            String newId = generateId(type);
        JsonApiResource resource = new JsonApiResource(newId, type, clone(attributes), clone(relationships));
            storage.computeIfAbsent(type, key -> new LinkedHashMap<>()).put(newId, resource);
            return resource;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public JsonApiResource update(String type, String id, ObjectNode attributes, ObjectNode relationships) {
        lock.writeLock().lock();
        try {
            Map<String, JsonApiResource> map = storage.computeIfAbsent(type, key -> new LinkedHashMap<>());
            JsonApiResource current = map.get(id);
            if (current == null) {
                throw new IllegalArgumentException("Resource not found");
            }
            ObjectNode updatedAttributes = attributes != null ? clone(attributes) : current.getAttributes();
            ObjectNode updatedRelationships = relationships != null ? clone(relationships) : current.getRelationships();
            JsonApiResource updated = new JsonApiResource(id, type, updatedAttributes, updatedRelationships);
            map.put(id, updated);
            return updated;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void delete(String type, String id) {
        lock.writeLock().lock();
        try {
            Map<String, JsonApiResource> map = storage.get(type);
            if (map != null) {
                map.remove(id);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private ObjectNode clone(ObjectNode node) {
        if (node == null) {
            return null;
        }
        try {
            return (ObjectNode) objectMapper.readTree(objectMapper.writeValueAsBytes(node));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to clone node", e);
        }
    }

    public List<JsonApiResource> filter(String type, Map<String, String> attributeFilters) {
        return findAll(type).stream()
                .filter(resource -> matchesAttributes(resource, attributeFilters))
                .collect(Collectors.toList());
    }

    private boolean matchesAttributes(JsonApiResource resource, Map<String, String> attributeFilters) {
        if (attributeFilters.isEmpty()) {
            return true;
        }
        ObjectNode attributes = resource.getAttributes();
        for (Map.Entry<String, String> entry : attributeFilters.entrySet()) {
            JsonNode node = attributes.get(entry.getKey());
            if (node == null || !Objects.equals(node.asText(), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    public List<JsonApiResource> findRelated(JsonApiResource parent, String relationshipName) {
        JsonNode relationshipNode = parent.getRelationship(relationshipName);
        if (relationshipNode == null) {
            return Collections.emptyList();
        }
        JsonNode dataNode = relationshipNode.get("data");
        if (dataNode == null) {
            return Collections.emptyList();
        }
        if (dataNode.isObject()) {
            String type = dataNode.path("type").asText();
            String id = dataNode.path("id").asText();
            return findById(type, id).map(List::of).orElseGet(List::of);
        }
        if (dataNode.isArray()) {
            List<JsonApiResource> related = new ArrayList<>();
            for (JsonNode item : dataNode) {
                String type = item.path("type").asText();
                String id = item.path("id").asText();
                findById(type, id).ifPresent(related::add);
            }
            return related;
        }
        return Collections.emptyList();
    }

    public ArrayNode toArrayNode(List<JsonApiResource> resources) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        for (JsonApiResource resource : resources) {
            arrayNode.add(resource.toResponse(objectMapper));
        }
        return arrayNode;
    }

    public ObjectNode toObjectNode(JsonApiResource resource) {
        return resource.toResponse(objectMapper);
    }

    public List<JsonApiResource> sort(List<JsonApiResource> resources, List<String> fields) {
        if (fields.isEmpty()) {
            return resources;
        }
        Comparator<JsonApiResource> comparator = null;
        for (String field : fields) {
            boolean desc = field.startsWith("-");
            String attributeName = desc ? field.substring(1) : field;
            Comparator<JsonApiResource> fieldComparator = Comparator.comparing(
                    resource -> Optional.ofNullable(resource.getAttribute(attributeName))
                            .map(JsonNode::asText)
                            .orElse("")
            );
            if (desc) {
                fieldComparator = fieldComparator.reversed();
            }
            comparator = comparator == null ? fieldComparator : comparator.thenComparing(fieldComparator);
        }
        return resources.stream().sorted(comparator).collect(Collectors.toList());
    }

    public List<JsonApiResource> paginate(List<JsonApiResource> resources, int pageNumber, int pageSize) {
        if (pageSize <= 0) {
            return resources;
        }
        int fromIndex = Math.max(0, (pageNumber - 1) * pageSize);
        if (fromIndex >= resources.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(resources.size(), fromIndex + pageSize);
        return resources.subList(fromIndex, toIndex);
    }

    private String generateId(String type) {
        Set<String> ids = storage.getOrDefault(type, Collections.emptyMap()).keySet();
        long max = ids.stream()
                .filter(StringUtils::hasText)
                .map(this::toLong)
                .max(Long::compare)
                .orElse(0L);
        return String.valueOf(max + 1);
    }

    private long toLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return value.hashCode();
        }
    }

    public ObjectNode getRelationshipData(JsonApiResource resource, String relationshipName) {
        JsonNode relationshipNode = resource.getRelationship(relationshipName);
        if (relationshipNode == null || !relationshipNode.has("data")) {
            return null;
        }
        if (relationshipNode.isObject()) {
            return (ObjectNode) relationshipNode;
        }
        if (relationshipNode.get("data").isObject() || relationshipNode.get("data").isArray()) {
            return (ObjectNode) relationshipNode;
        }
        return null;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
